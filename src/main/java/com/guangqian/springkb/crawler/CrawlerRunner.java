package com.guangqian.springkb.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 爬虫入口：`mvnw spring-boot:run -Dspring-boot.run.arguments="--crawl=vue,springai,springboot"`
 * 或 `--crawl=all` 抓全部源。不带参数则跳过（不影响正常启动）。
 * 每源篇数由 `--crawl-limit=N` 控制（默认 80，N=0 表示不限）。
 * 样本 JSON 落盘到 data/raw/<source>.json。
 */
@Component
public class CrawlerRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlerRunner.class);

    /** 缺省每源抓取篇数。 */
    private static final int DEFAULT_LIMIT = 80;

    private final List<DocumentCrawler> crawlers;
    private final JsonMapper jsonMapper;

    public CrawlerRunner(List<DocumentCrawler> crawlers, JsonMapper jsonMapper) {
        this.crawlers = crawlers;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> opts = args.getOptionValues("crawl");
        if (opts == null || opts.isEmpty()) {
            return; // 未指定 --crawl，正常运行不爬取
        }
        Set<String> wanted = "all".equals(opts.get(0)) ? null : Set.of(opts.get(0).split(","));
        int limit = resolveLimit(args);
        Path dir = Path.of("data", "raw");
        Files.createDirectories(dir);
        log.info("开始爬取 源={} 每源上限={}", wanted == null ? "all" : wanted, limit);
        for (DocumentCrawler crawler : crawlers) {
            if (wanted != null && !wanted.contains(crawler.source())) {
                continue;
            }
            try {
                List<RawDocument> docs = crawler.crawlSample(limit);
                Path out = dir.resolve(crawler.source() + ".json");
                if (docs.isEmpty()) {
                    log.warn("爬取结果为空 source={}，保留既有文件不做覆盖 -> {}", crawler.source(), out.toAbsolutePath());
                    continue;
                }
                Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(docs));
                log.info("爬取完成 source={} count={} -> {}", crawler.source(), docs.size(), out.toAbsolutePath());
            } catch (Exception e) {
                log.error("爬取失败 source={}", crawler.source(), e);
            }
        }
    }

    /** `--crawl-limit=N`：每源最多抓取篇数，缺省 80，0 表示不限，非法值回落默认。 */
    private static int resolveLimit(ApplicationArguments args) {
        List<String> values = args.getOptionValues("crawl-limit");
        String raw = values == null || values.isEmpty() ? null : values.get(0);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 0 ? value : DEFAULT_LIMIT;
        } catch (NumberFormatException e) {
            log.warn("非法的 crawl-limit={}，回落到默认 {}", raw, DEFAULT_LIMIT);
            return DEFAULT_LIMIT;
        }
    }
}
