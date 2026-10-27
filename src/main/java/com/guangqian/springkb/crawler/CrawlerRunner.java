package com.guangqian.springkb.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

/**
 * 爬虫入口：`mvnw spring-boot:run -Dspring-boot.run.arguments="--crawl=vue,springai,springboot"`
 * 或 `--crawl=all` 抓全部源。不带参数则跳过（不影响正常启动）。
 * 每源篇数由 `--crawl-limit=N` 控制（默认 80，N=0 表示不限）。
 * 去重与断点续抓由 {@link CrawlCheckpoint} 负责：已完成的源默认跳过，中断的源从已抓页面继续；
 * 需要整源重爬时加 `--crawl-force`。
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
        boolean force = args.containsOption("crawl-force");
        Path dir = Path.of("data", "raw");
        Files.createDirectories(dir);
        log.info("开始爬取 源={} 每源上限={} 强制重爬={}", wanted == null ? "all" : wanted, limit, force);
        for (DocumentCrawler crawler : crawlers) {
            String source = crawler.source();
            if (wanted != null && !wanted.contains(source)) {
                continue;
            }
            if (!force && CrawlCheckpoint.isDone(source)) {
                log.info("跳过已完成源 source={}（需要整源重爬请加 --crawl-force）", source);
                continue;
            }
            try {
                List<RawDocument> partial = CrawlCheckpoint.loadPartial(source);
                if (!partial.isEmpty()) {
                    log.info("发现断点 source={} 已缓存 {} 篇，继续抓取剩余页面", source, partial.size());
                }
                CrawlCheckpoint.markPartial(source, partial.size());
                List<RawDocument> fetched = crawler.crawlSample(limit);
                List<RawDocument> docs = CrawlCheckpoint.merge(partial, fetched);
                Path out = dir.resolve(source + ".json");
                if (docs.isEmpty()) {
                    log.warn("爬取结果为空 source={}，保留既有文件不做覆盖 -> {}", source, out.toAbsolutePath());
                    continue;
                }
                writeAtomically(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(docs));
                CrawlCheckpoint.clearPartial(source);
                CrawlCheckpoint.markDone(source, docs.size());
                log.info("爬取完成 source={} count={}（断点续抓 {} 篇）-> {}",
                        source, docs.size(), partial.size(), out.toAbsolutePath());
            } catch (Exception e) {
                log.error("爬取失败 source={}", source, e);
            }
        }
    }

    /** 先写临时文件再原子替换，避免进程被杀时留下半截 JSON。 */
    private static void writeAtomically(Path target, String content) throws Exception {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
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
