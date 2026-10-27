#requires -Version 5.1
<#
.SYNOPSIS
    爬虫进度监测面板（以断点状态文件为准，不依赖日志编码）。

.DESCRIPTION
    数据来源（按权威性排序）：
    
      1) data/raw/.crawl-state.json  —— 各源状态 done/pending、已入库篇数、更新时间
      2) data/raw/.partial/*.urls    —— 正在抓取的源实时进度（已抓 URL 行数）
      3) data/raw/.partial/*.jsonl   —— 增量落盘大小（进程被 kill 也不丢）
      4) logs/crawl-*.log            —— 进程 PID、最近活动、候选数与失败 URL

    注意：日志由 PowerShell 重定向写入时中文会被破坏且不可还原（JVM 的 UTF-8 先被按
    控制台代码页解码、再按默认编码写盘），所以进度一律取自状态/断点文件，
    日志只取 ASCII 字段（时间戳 / PID / 数字 / URL / [source] 标签）。

.EXAMPLE
    .\scripts\monitor-crawl.ps1 -Once                 # 只看一次快照
    .\scripts\monitor-crawl.ps1 -IntervalSeconds 5    # 每 5 秒刷新，爬虫结束自动退出
    .\scripts\monitor-crawl.ps1 -LogPath logs\crawl-resume.log -Once
#>
param(
    [switch]$Once,
    [int]$IntervalSeconds = 5,
    [string]$LogPath = ''
)

$root = Split-Path -Parent $PSScriptRoot
$statePath = Join-Path $root 'data\raw\.crawl-state.json'
$partialDir = Join-Path $root 'data\raw\.partial'
$rawDir = Join-Path $root 'data\raw'

# 全部已注册爬虫源：状态文件里没有的新源也要显示为「等待」
$Known = @(
    'vue', 'vite', 'vuerouter', 'pinia', 'django', 'springai', 'springboot',
    'springframework', 'springsecurity', 'springdatajpa', 'stackoverflow',
    'fastapi', 'go', 'kafka', 'kubernetes', 'langchain', 'mysql', 'nodejs',
    'postgresql', 'react', 'python', 'rust', 'typescript', 'docker',
    'nacos', 'sentinel', 'dubbo', 'seata', 'rocketmq', 'elasticsearch', 'nginx'
)

if (-not $LogPath) {
    $candidates = Get-ChildItem (Join-Path $root 'logs\crawl-*.log') -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    $LogPath = if ($candidates) { $candidates[0].FullName } else { Join-Path $root 'logs\crawl-resume.log' }
}

$script:Prev = @{}   # name -> @{ Ts; Urls }，用于计算刷新间隔的抓取速率

# ---------- 日志读取（自动识别编码） ----------

function Read-LogLines {
    param([string]$Path, [int]$MaxBytes = 512000)
    if (-not (Test-Path $Path)) { return @() }
    $fs = $null
    try {
        # 爬虫进程把重定向日志一直开着写，必须用 FileShare.ReadWrite 才读得到
        $fs = New-Object IO.FileStream($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        $len = [int][Math]::Min($fs.Length, $MaxBytes)
        if ($len -le 0) { return @() }
        $null = $fs.Seek(-$len, [IO.SeekOrigin]::End)
        $buf = New-Object byte[] $len
        $read = 0
        while ($read -lt $len) {
            $n = $fs.Read($buf, $read, $len - $read)
            if ($n -le 0) { break }
            $read += $n
        }
        if ($read -le 0) { return @() }
        if ($read -lt $len) { $buf = $buf[0..($read - 1)] }
    }
    catch { return @() }
    finally { if ($fs) { $fs.Dispose() } }

    # 按换行边界裁剪，避免尾字节把多字节字符切断
    $cut = $buf.Length
    for ($i = $buf.Length - 1; $i -ge 0 -and $i -gt $buf.Length - 4096; $i--) {
        if ($buf[$i] -eq 10) { $cut = $i + 1; break }
    }
    if ($cut -gt 0 -and $cut -le $buf.Length) { $buf = $buf[0..($cut - 1)] }

    # 先按「严格 UTF-8」解码：能解通就是 UTF-8；否则按 GBK（PowerShell 5.1 重定向常见）
    $strict = New-Object Text.UTF8Encoding($false, $true)
    try {
        $text = $strict.GetString($buf)
    }
    catch {
        try { $text = [Text.Encoding]::GetEncoding(936).GetString($buf) }
        catch { $text = [Text.Encoding]::UTF8.GetString($buf) }
    }
    return ($text -split "`r?`n")
}

function Get-LogSummary {
    param([string[]]$Lines)

    $pid_ = $null
    $lastSource = $null
    $lastTs = $null
    $candidates = @{}
    $failUrls = New-Object System.Collections.Generic.List[string]

    # 中文在 PowerShell 重定向时已被破坏（JVM 的 UTF-8 被按控制台代码页解码后再编码），
    # 无法还原，只能依赖 ASCII 部分：PID、时间戳、URL、数字、[source] 标签。
    foreach ($line in $Lines) {
        if (-not $line) { continue }
        if (-not $pid_ -and $line -match 'with PID (\d+)') { $pid_ = [int]$Matches[1] }
        if ($line -match '^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})') { $lastTs = $Matches[1] }
        # 一行里可能有多个 [tag]（如 [ main] / [elasticsearch]），逐个判断哪个是数据源
        foreach ($m in [regex]::Matches($line, '\[([a-z][a-z0-9]*)\]')) {
            $name = $m.Groups[1].Value
            if ($Known -contains $name) {
                $lastSource = $name
                if ($line -match 'sitemap[^0-9]{0,40}(\d+)[^0-9]{0,20}URL') { $candidates[$name] = [int]$Matches[1] }
            }
        }
        if ($line -match 'Connection reset|handshake|timed out|Timeout|SSLException') {
            if ($line -match '(https?://\S+)') { $failUrls.Add($Matches[1]) }
        }
    }

    # 展示用：剔除乱码字符，只保留可读 ASCII
    $tail = @($Lines | Where-Object { $_ -match '^\d{4}-\d{2}-\d{2}T' } |
            ForEach-Object { ($_ -replace '[^\x20-\x7E]', '') -replace '\s{2,}', ' ' } |
            Select-Object -Last 3)

    [pscustomobject]@{
        Pid        = $pid_
        LastSource = $lastSource
        LastTs     = $lastTs
        Candidates = $candidates
        FailUrls   = $failUrls
        Tail       = $tail
    }
}

# ---------- 状态与断点 ----------

function Read-CrawlState {
    if (-not (Test-Path $statePath)) { return @{} }
    try {
        $obj = Get-Content $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    catch { return @{} }
    $map = @{}
    foreach ($p in $obj.PSObject.Properties) {
        $map[$p.Name] = [pscustomobject]@{
            Status = [string]$p.Value.status
            Docs   = if ($null -ne $p.Value.docs) { [int]$p.Value.docs } else { 0 }
            UpdatedAt = [string]$p.Value.updatedAt
        }
    }
    return $map
}

function Read-PartialProgress {
    $map = @{}
    if (-not (Test-Path $partialDir)) { return $map }
    Get-ChildItem $partialDir -Filter '*.urls' -ErrorAction SilentlyContinue | ForEach-Object {
        $name = $_.BaseName
        $urls = 0
        try { $urls = (Get-Content $_.FullName -ErrorAction Stop).Count } catch { $urls = 0 }
        $jsonl = Join-Path $partialDir ($name + '.jsonl')
        $kb = 0
        if (Test-Path $jsonl) { $kb = [math]::Round((Get-Item $jsonl).Length / 1KB) }
        $map[$name] = [pscustomobject]@{
            Urls      = $urls
            KB        = $kb
            UpdatedAt = $_.LastWriteTime
        }
    }
    return $map
}

function Get-Snapshot {
    $lines = Read-LogLines -Path $LogPath
    $log = Get-LogSummary -Lines $lines
    $state = Read-CrawlState
    $progress = Read-PartialProgress

    $names = @($Known) + @($state.Keys) + @($progress.Keys) + @($log.Candidates.Keys)
    $names = @($names | Where-Object { $_ } | Select-Object -Unique)

    $now = Get-Date
    $rows = @()
    foreach ($name in $names) {
        $st = $state[$name]
        $pg = $progress[$name]
        $isDone = ($st -and $st.Status -eq 'done')
        $isActive = ($log.LastSource -eq $name)

        if ($isDone) {
            $status = '完成'
            $count = if ($st.Docs -gt 0) { $st.Docs } else { '' }
        }
        elseif ($pg) {
            $status = '抓取中'
            $count = $pg.Urls
        }
        elseif ($isActive -and -not $isDone) {
            $status = '抓取中'
            $count = 0
        }
        else {
            $status = '等待'
            $count = 0
        }

        # 速率：按两次快照的增量计算（-Once 时为空白）
        $rate = ''
        if ($status -eq '抓取中') {
            $prev = $script:Prev[$name]
            if ($prev -and $now -gt $prev.Ts -and $count -ge $prev.Urls) {
                $sec = ($now - $prev.Ts).TotalSeconds
                if ($sec -ge 1) { $rate = '{0:N0}/min' -f (($count - $prev.Urls) / $sec * 60) }
            }
            $script:Prev[$name] = @{ Ts = $now; Urls = $count }
        }
        else {
            $script:Prev.Remove($name)
        }

        $cand = if ($log.Candidates.ContainsKey($name)) { $log.Candidates[$name] } else { '' }
        $kb = if ($pg) { $pg.KB } elseif ($isDone -and (Test-Path (Join-Path $rawDir ($name + '.json')))) { [math]::Round((Get-Item (Join-Path $rawDir ($name + '.json'))).Length / 1KB) } else { '' }

        $rows += [pscustomobject]@{
            数据源 = $name
            状态   = $status
            进度   = $count
            候选   = $cand
            落盘KB = $kb
            速率   = $rate
        }
    }

    $alive = $log.Pid -and (Get-Process -Id $log.Pid -ErrorAction SilentlyContinue)
    [pscustomobject]@{
        Rows       = $rows
        Pid        = $log.Pid
        Alive      = [bool]$alive
        LastSource = $log.LastSource
        LastTs     = $log.LastTs
        FailUrls   = $log.FailUrls
        Tail       = $log.Tail
        Names      = $names
    }
}

function Show-Snapshot {
    param($Snap)

    $rank = @{ '抓取中' = 0; '等待' = 1; '完成' = 2 }
    $rows = @($Snap.Rows | Sort-Object @{ Expression = { $rank[$_.状态] } }, @{ Expression = { $_.数据源 } })

    $statusText = if ($Snap.Alive) { "运行中 (PID $($Snap.Pid))" } else { '已结束（进程不存在）' }
    Write-Host "=== 爬虫监测  $(Get-Date -Format 'HH:mm:ss')  $statusText ===" -ForegroundColor Cyan
    Write-Host ("日志: {0}    最近活动: {1}" -f $LogPath, ($(if ($Snap.LastTs) { $Snap.LastTs } else { '-' }))) -ForegroundColor DarkGray
    Write-Host ''

    $rows | Format-Table -AutoSize | Out-String | Write-Host

    $done = @($Snap.Rows | Where-Object { $_.状态 -eq '完成' })
    $running = @($Snap.Rows | Where-Object { $_.状态 -eq '抓取中' -or $_.状态 -eq '写入中' })
    $waiting = @($Snap.Rows | Where-Object { $_.状态 -eq '等待' })
    $known = @($done | Where-Object { $_.进度 -ne '' })
    $doneDocs = ($known | Measure-Object -Property 进度 -Sum).Sum
    $noDoc = $done.Count - $known.Count
    $docText = if ($noDoc -gt 0) { "已登记 {0} 篇（另有 {1} 源状态未记录篇数）" -f [int]$doneDocs, $noDoc } else { "已登记 {0} 篇" -f [int]$doneDocs }
    Write-Host ("完成 {0} 源 / 共 {1} 源；{2}；抓取中 {3} 源；等待 {4} 源" -f `
        $done.Count, $Snap.Names.Count, $docText, $running.Count, $waiting.Count) -ForegroundColor Green

    if ($Snap.FailUrls.Count -gt 0) {
        $distinct = @($Snap.FailUrls | Select-Object -Unique)
        Write-Host ("失败请求 {0} 次（去重 {1} 个 URL），最近: {2}" -f `
            $Snap.FailUrls.Count, $distinct.Count, $Snap.FailUrls[$Snap.FailUrls.Count - 1]) -ForegroundColor DarkYellow
    }

    if ($Snap.Tail.Count -gt 0) {
        Write-Host ''
        Write-Host '最近日志:' -ForegroundColor DarkGray
        $Snap.Tail | ForEach-Object { Write-Host ('  ' + $_) -ForegroundColor DarkGray }
    }
}

if ($Once) {
    Show-Snapshot (Get-Snapshot)
}
else {
    while ($true) {
        $snap = Get-Snapshot
        Clear-Host
        Show-Snapshot $snap
        if (-not $snap.Alive) { break }
        Start-Sleep -Seconds $IntervalSeconds
    }
}
