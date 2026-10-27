package com.guangqian.springkb.ingestion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.guangqian.springkb.crawler.RawDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 导入入口：`--import=vue,springai,springboot` 或 `--import=all` 时从 data/raw/{source}.json 读取并导入。
 * `--import=all` 自动发现 data/raw 下的全部 JSON，新增爬虫源无需改代码。
 * 不带参数则跳过（不影响正常启动）；文件缺失/解析失败只记录日志不中断启动。
 */
@Component
public class ImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

    private static final TypeReference<List<RawDocument>> RAW_DOC_LIST = new TypeReference<>() {
    };

    private final IngestionService ingestionService;
    private final JsonMapper jsonMapper;

    public ImportRunner(IngestionService ingestionService, JsonMapper jsonMapper) {
        this.ingestionService = ingestionService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> opts = args.getOptionValues("import");
        if (opts == null || opts.isEmpty()) {
            return; // 未指定 --import，正常运行不导入
        }
        Set<String> wanted = "all".equals(opts.get(0)) ? null : Set.of(opts.get(0).split(","));
        Path dir = Path.of("data", "raw");
        List<String> sources = resolveSources(dir, wanted);
        if (sources.isEmpty()) {
            log.warn("没有可导入的数据文件 dir={}", dir.toAbsolutePath());
            return;
        }
        log.info("开始导入 源数={} 源={}", sources.size(), sources);
        long startNs = System.nanoTime();
        List<SourceResult> results = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            int index = i + 1;
            Path file = dir.resolve(source + ".json");
            if (!Files.exists(file)) {
                log.error("导入文件不存在，跳过 source={} file={}", source, file.toAbsolutePath());
                continue;
            }
            try {
                List<RawDocument> docs = jsonMapper.readValue(Files.readString(file), RAW_DOC_LIST);
                long sourceStartNs = System.nanoTime();
                ProgRenderer renderer = new ProgRenderer(index, sources.size(), source, docs.size(), sourceStartNs);
                ImportSummary summary = ingestionService.importAll(docs, renderer::onProgress);
                renderer.finish(summary);
                results.add(new SourceResult(source, docs.size(), summary));
            } catch (Exception e) {
                log.error("导入失败 source={}", source, e);
            }
        }
        printReport(results, sources.size(), (System.nanoTime() - startNs) / 1_000_000_000.0);
    }

    /** 单源导入结果，用于收尾报表。 */
    private record SourceResult(String source, int total, ImportSummary summary) {
    }

    /**
     * 进度渲染器：真正的控制台（System.console() 非空）用 {@code \r} 原地刷新一行；
     * 输出被重定向到文件时退化成分段日志行，避免日志被回车符糊成一行。
     * 进度条只用 ASCII 字符，规避 Windows 控制台/重定向的编码问题。
     */
    private static final class ProgRenderer {

        private static final int BAR_WIDTH = 28;
        private static final boolean INTERACTIVE = System.console() != null;

        private final int index;
        private final int totalSources;
        private final String source;
        private final int totalDocs;
        private final long startNs;
        private long lastRenderMs;

        ProgRenderer(int index, int totalSources, String source, int totalDocs, long startNs) {
            this.index = index;
            this.totalSources = totalSources;
            this.source = source;
            this.totalDocs = totalDocs;
            this.startNs = startNs;
        }

        void onProgress(int processed, ImportSummary soFar) {
            long now = System.currentTimeMillis();
            // 交互模式 5 次/秒，重定向模式 1 次/秒 + 完成时必须输出
            if (processed < totalDocs && now - lastRenderMs < (INTERACTIVE ? 200 : 1000)) {
                return;
            }
            lastRenderMs = now;
            String line = line(processed, soFar);
            if (INTERACTIVE) {
                System.out.print('\r' + line);
                System.out.flush();
            } else {
                log.info("导入进度 {}", line);
            }
        }

        void finish(ImportSummary summary) {
            String line = line(totalDocs, summary);
            if (INTERACTIVE) {
                System.out.print('\r' + line);
                System.out.println();
            } else {
                log.info("导入完成 {}", line);
            }
        }

        private String line(int processed, ImportSummary s) {
            int pct = totalDocs == 0 ? 100 : (int) (processed * 100L / totalDocs);
            int filled = totalDocs == 0 ? BAR_WIDTH : pct * BAR_WIDTH / 100;
            double sec = Math.max(0.001, (System.nanoTime() - startNs) / 1_000_000_000.0);
            double rate = processed / sec;
            long etaSec = rate > 0 ? (long) ((totalDocs - processed) / rate) : 0;
            return String.format("[%d/%d] %-14s [%s%s] %3d%% %d/%d ok=%d skip=%d fail=%d %.1f/s ETA %s",
                    index, totalSources, source,
                    "=".repeat(filled), "-".repeat(BAR_WIDTH - filled),
                    pct, processed, totalDocs, s.imported(), s.skipped(), s.failed(), rate, mmss(etaSec));
        }
    }

    private void printReport(List<SourceResult> results, int totalSources, double elapsedSec) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int docs = 0;
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append("==================== 导入汇总 ====================").append(System.lineSeparator());
        sb.append(String.format("%-16s %8s %8s %8s %8s%n", "源", "总数", "导入", "跳过", "失败"));
        for (SourceResult r : results) {
            ImportSummary s = r.summary();
            sb.append(String.format("%-16s %8d %8d %8d %8d%n",
                    r.source(), r.total(), s.imported(), s.skipped(), s.failed()));
            docs += r.total();
            imported += s.imported();
            skipped += s.skipped();
            failed += s.failed();
        }
        sb.append("-------------------------------------------------").append(System.lineSeparator());
        sb.append(String.format("%-16s %8d %8d %8d %8d%n", "合计(" + results.size() + "/" + totalSources + " 源)",
                docs, imported, skipped, failed));
        sb.append(String.format("耗时 %s（文档 %.1f/s）", mmss((long) elapsedSec),
                elapsedSec > 0 ? docs / elapsedSec : 0)).append(System.lineSeparator());
        sb.append("=================================================");
        if (System.console() != null) {
            System.out.println(sb);
        } else {
            log.info("{}", sb);
        }
    }

    private static String mmss(long sec) {
        if (sec >= 3600) {
            return String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
        }
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    /** 显式指定源时按参数；`all` 时扫描 data/raw 下全部 *.json（文件名即 source）。 */
    private static List<String> resolveSources(Path dir, Set<String> wanted) throws Exception {
        if (wanted != null) {
            return wanted.stream().sorted().toList();
        }
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    // 跳过断点续抓的隐藏文件（.crawl-state.json 不是文档源）
                    .filter(name -> name.endsWith(".json") && !name.startsWith("."))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }
}
