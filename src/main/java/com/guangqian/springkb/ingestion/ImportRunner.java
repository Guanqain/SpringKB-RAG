package com.guangqian.springkb.ingestion;

import java.nio.file.Files;
import java.nio.file.Path;
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
        log.info("开始导入 源={}", sources);
        for (String source : sources) {
            Path file = dir.resolve(source + ".json");
            if (!Files.exists(file)) {
                log.error("导入文件不存在，跳过 source={} file={}", source, file.toAbsolutePath());
                continue;
            }
            try {
                List<RawDocument> docs = jsonMapper.readValue(Files.readString(file), RAW_DOC_LIST);
                ImportSummary summary = ingestionService.importAll(docs);
                log.info("导入完成 source={} imported={} skipped={} failed={}",
                        source, summary.imported(), summary.skipped(), summary.failed());
            } catch (Exception e) {
                log.error("导入失败 source={}", source, e);
            }
        }
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
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }
}
