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
 * 样本 JSON 落盘到 data/raw/<source>.json。
 */
@Component
public class CrawlerRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlerRunner.class);

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
        Path dir = Path.of("data", "raw");
        Files.createDirectories(dir);
        for (DocumentCrawler crawler : crawlers) {
            if (wanted != null && !wanted.contains(crawler.source())) {
                continue;
            }
            try {
                List<RawDocument> docs = crawler.crawlSample(80);
                Path out = dir.resolve(crawler.source() + ".json");
                Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(docs));
                log.info("爬取完成 source={} count={} -> {}", crawler.source(), docs.size(), out.toAbsolutePath());
            } catch (Exception e) {
                log.error("爬取失败 source={}", crawler.source(), e);
            }
        }
    }
}
