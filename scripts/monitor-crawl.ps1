#requires -Version 5.1
<#
.SYNOPSIS
    爬虫实时监测面板：解析 logs\crawl-full.log 与 data\raw\*.json，输出各源进度。

.DESCRIPTION
    日志由 PowerShell 重定向写入，中文部分已损坏，脚本只保留 ASCII 后解析
    「source=xxx count=N」「[src] N」「[src] sitemap ... N」等关键字段。

.EXAMPLE
    .\scripts\monitor-crawl.ps1            # 每 3 秒刷新
    .\scripts\monitor-crawl.ps1 -Once      # 只输出一次快照
#>
param(
    [int]$IntervalSeconds = 3,
    [switch]$Once,
    [string]$LogPath = ''
)

$root = Split-Path -Parent $PSScriptRoot
if ($LogPath) {
    $logPath = $LogPath
}
elseif (Test-Path (Join-Path $root 'logs\crawl-remaining.log')) {
    $logPath = Join-Path $root 'logs\crawl-remaining.log'
}
else {
    $logPath = Join-Path $root 'logs\crawl-full.log'
}
$rawDir = Join-Path $root 'data\raw'
$defaultOrder = @('docker', 'typescript', 'django', 'rust', 'react', 'postgresql',
    'langchain', 'kubernetes', 'python', 'go', 'nodejs', 'mysql', 'kafka', 'fastapi')

function Get-CrawlSnapshot {
    param([string]$LogPath, [string]$RawDir)

    $lines = @()
    if (Test-Path $LogPath) {
        $lines = Get-Content $LogPath -Tail 8000 -ErrorAction SilentlyContinue
    }

    # 中文已被重定向破坏，先剔除不可读字符，只留 ASCII 关键信息
    $ascii = @()
    foreach ($raw in $lines) { $ascii += ((($raw -replace '[^\x20-\x7E]', '') -replace '\s+', ' ').Trim()) }

    $order = @()
    $crawlerPid = $null
    foreach ($line in $ascii) {
        if (-not $crawlerPid -and $line -match 'INFO (\d+) ---') { $crawlerPid = [int]$Matches[1] }
        # 源列表形如 [react, mysql, ...]；要求含逗号，避开 [springKB] / [main] 等日志标签
        if ($order.Count -eq 0 -and $line -match '\[([a-z]+(?:\s*,\s*[a-z]+)+)\]') {
            $order = @(($Matches[1] -split ',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        }
    }
    if ($order.Count -eq 0) { $order = $defaultOrder }
    $nameAlt = ($order -join '|')

    $sources = [ordered]@{}
    $lastUrl = ''
    $lastTs = $null

    foreach ($line in $ascii) {
        $ts = $null
        if ($line -match '^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})') {
            $ts = [datetime]::ParseExact($Matches[1], 'yyyy-MM-ddTHH:mm:ss', $null)
            $lastTs = $ts
        }

        # 完成行：CrawlerRunner 的 source=xxx count=N（该行只带 [springKB] 应用标签）
        if ($line -match 'source=(\w+) count=(\d+)') {
            $name = $Matches[1]
            if (-not $sources.Contains($name)) { $sources[$name] = New-SourceState $name }
            $sources[$name].Done = $true
            $sources[$name].Count = [int]$Matches[2]
            $sources[$name].LastCount = [int]$Matches[2]
            if ($ts) { $sources[$name].LastTs = $ts }
            continue
        }

        if ($line -notmatch "\[($nameAlt)\]") {
            if ($line -match 'Connection reset|handshake|timed out|Timeout') {
                if ($line -match '(https?://\S+)') { $lastUrl = $Matches[1] }
            }
            continue
        }

        $name = $Matches[1]
        if (-not $sources.Contains($name)) { $sources[$name] = New-SourceState $name }
        $entry = $sources[$name]
        if ($ts) { $entry.LastTs = $ts }

        if ($line -match '\bsitemap\b.*?\s(\d+)\s') {
            $entry.Candidates = [int]$Matches[1]
        }
        elseif ($line -match "\[\w+\]\s+(\d+)") {
            $count = [int]$Matches[1]
            if (-not $entry.Done -and $count -ge $entry.Count) {
                if (-not $entry.FirstTs -and $ts) {
                    $entry.FirstTs = $ts
                    $entry.FirstCount = $count
                }
                $entry.Count = $count
                $entry.LastCount = $count
            }
        }

        if ($line -match 'Connection reset|handshake|timed out|Timeout') {
            $entry.Fails++
            if ($line -match '(https?://\S+)') { $lastUrl = $Matches[1] }
        }
    }

    $active = $null
    foreach ($key in $sources.Keys) {
        $s = $sources[$key]
        if (-not $s.Done -and (-not $active -or ($s.LastTs -and $s.LastTs -gt $sources[$active].LastTs))) {
            $active = $key
        }
    }

    [pscustomobject]@{
        Order      = $order
        Sources    = $sources
        CrawlerPid = $crawlerPid
        Active     = $active
        LastUrl    = $lastUrl
        LastTs     = $lastTs
    }
}

function New-SourceState {
    param([string]$Name)
    [pscustomobject]@{
        Name       = $Name
        Count      = 0
        Candidates = 0
        Done       = $false
        Fails      = 0
        FirstTs    = $null
        FirstCount = 0
        LastTs     = $null
        LastCount  = 0
    }
}

function Show-CrawlSnapshot {
    param($Snap, [switch]$NoClear)

    if (-not $NoClear) { Clear-Host }
    $alive = $Snap.CrawlerPid -and (Get-Process -Id $Snap.CrawlerPid -ErrorAction SilentlyContinue)
    $statusText = if ($alive) { "运行中 (PID $($Snap.CrawlerPid))" } else { "已结束或进程不存在" }
    Write-Host "=== 爬虫监测  $(Get-Date -Format 'HH:mm:ss')  $statusText ===" -ForegroundColor Cyan
    Write-Host ""

    $rows = @()
    $i = 0
    foreach ($name in $Snap.Order) {
        $i++
        $s = $Snap.Sources[$name]
        if (-not $s) {
            $rows += [pscustomobject]@{ 序号 = $i; 数据源 = $name; 状态 = '等待'; 已抓 = 0; 候选 = ''; 失败 = ''; 速率 = '' }
            continue
        }
        $rate = ''
        if ($s.Done) {
            $state = '完成'
        }
        elseif ($Snap.Active -eq $name) {
            $state = '抓取中'
            if ($s.FirstTs -and $s.LastTs -and $s.LastCount -gt $s.FirstCount) {
                $sec = ($s.LastTs - $s.FirstTs).TotalSeconds
                if ($sec -gt 0) { $rate = '{0:N1}/min' -f (($s.LastCount - $s.FirstCount) / $sec * 60) }
            }
        }
        else {
            $state = '等待'
        }
        $rows += [pscustomobject]@{
            序号   = $i
            数据源 = $name
            状态   = $state
            已抓   = $s.Count
            候选   = if ($s.Candidates -gt 0) { $s.Candidates } else { '' }
            失败   = if ($s.Fails -gt 0) { $s.Fails } else { '' }
            速率   = $rate
        }
    }

    # 抓取中置顶，已完成次之，等待排最后
    $rank = @{ '抓取中' = 0; '完成' = 1; '等待' = 2 }
    $rows = @($rows | Sort-Object @{ Expression = { $rank[$_.状态] } }, @{ Expression = { $_.数据源 } })
    for ($n = 0; $n -lt $rows.Count; $n++) { $rows[$n].序号 = $n + 1 }

    $rows | Format-Table -AutoSize | Out-String | Write-Host

    $doneCount = 0
    $totalDocs = 0
    $totalFails = 0
    foreach ($s in $Snap.Sources.Values) {
        if ($s.Done) { $doneCount++ }
        $totalDocs += $s.Count
        $totalFails += $s.Fails
    }
    Write-Host "已完成源: $doneCount / $($Snap.Order.Count)    已抓文档合计: $totalDocs    失败请求: $totalFails" -ForegroundColor Green
    if ($Snap.LastUrl) { Write-Host "最近失败 URL: $($Snap.LastUrl)" -ForegroundColor DarkYellow }
    Write-Host ""

    Write-Host "已落盘 JSON:" -ForegroundColor DarkGray
    Get-ChildItem (Join-Path $rawDir '*.json') -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 8 Name, @{n = 'KB'; e = { [math]::Round($_.Length / 1KB) } }, LastWriteTime |
        Format-Table -AutoSize | Out-String | Write-Host
}

if ($Once) {
    Show-CrawlSnapshot (Get-CrawlSnapshot -LogPath $logPath -RawDir $rawDir) -NoClear
}
else {
    while ($true) {
        $snap = Get-CrawlSnapshot -LogPath $logPath -RawDir $rawDir
        Show-CrawlSnapshot $snap
        $alive = $snap.CrawlerPid -and (Get-Process -Id $snap.CrawlerPid -ErrorAction SilentlyContinue)
        if (-not $alive) { break }
        Start-Sleep -Seconds $IntervalSeconds
    }
}
