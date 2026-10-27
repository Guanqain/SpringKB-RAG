package com.guangqian.springkb.crawler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 爬取断点记录：让被中断的抓取在下次启动时从已抓页面继续，而不是整源重来。
 *
 * <p>落盘结构（均在 data/raw 下）：
 * <ul>
 *   <li>{@code .crawl-state.json}：源 → 状态（done / partial）、已入库篇数、更新时间；</li>
 *   <li>{@code .partial/<source>.jsonl}：断点期间逐篇追加的 RawDocument（一行一篇）；</li>
 *   <li>{@code .partial/<source>.urls}：已抓 URL 列表，续抓时据此跳过，避免重复请求。</li>
 * </ul>
 * 整源抓取完成后由 {@link CrawlerRunner} 合并写入正式 JSON，再清理断点文件并标记 done。</p>
 */
public final class CrawlCheckpoint {

    private static final Logger log = LoggerFactory.getLogger(CrawlCheckpoint.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private static final String STATE_FILE = ".crawl-state.json";

    private static final String PARTIAL_DIR = ".partial";

    /** 数据根目录（data/raw），单测可替换为临时目录。 */
    private static Path baseDir = Path.of("data", "raw");

    private CrawlCheckpoint() {
    }

    /** 单测钩子：切换落盘根目录。 */
    static void useBaseDir(Path dir) {
        baseDir = dir;
    }

    record SourceState(String status, Integer docs, String updatedAt) {
    }

    /** 该源是否已完成过整源抓取。 */
    public static boolean isDone(String source) {
        SourceState state = readState().get(source);
        return state != null && "done".equals(state.status());
    }

    /** 标记为「抓取中/可续抓」，同时刷新已缓存篇数。 */
    public static void markPartial(String source, int docs) {
        writeState(source, "partial", docs);
    }

    /** 标记为整源完成。 */
    public static void markDone(String source, int docs) {
        writeState(source, "done", docs);
    }

    /** 已抓 URL 集合；无断点文件时返回空集合。 */
    public static Set<String> doneUrls(String source) {
        Path file = urlsFile(source);
        if (!Files.exists(file)) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("断点 URL 读取失败 source={} : {}", source, e.getMessage());
            return Set.of();
        }
    }

    /** 读取断点期间已落盘的文档。 */
    public static List<RawDocument> loadPartial(String source) {
        Path file = docsFile(source);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<RawDocument> docs = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    docs.add(MAPPER.readValue(line, RawDocument.class));
                } catch (Exception e) {
                    log.warn("断点文档行解析失败 source={} : {}", source, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("断点文档读取失败 source={} : {}", source, e.getMessage());
        }
        return docs;
    }

    /** 逐篇追加断点记录（文档 + URL），异常只告警不中断抓取。 */
    public static void append(String source, RawDocument doc) {
        if (doc == null) {
            return;
        }
        try {
            Files.createDirectories(partialDir());
            Files.writeString(docsFile(source), MAPPER.writeValueAsString(doc) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (doc.url() != null && !doc.url().isBlank()) {
                Files.writeString(urlsFile(source), doc.url() + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            log.warn("断点写入失败 source={} : {}", source, e.getMessage());
        }
    }

    /** 合并断点文档与本次新抓文档，按 URL 去重，保持先后顺序。 */
    public static List<RawDocument> merge(List<RawDocument> partial, List<RawDocument> fresh) {
        Map<String, RawDocument> byKey = new LinkedHashMap<>();
        for (RawDocument doc : partial) {
            put(byKey, doc);
        }
        for (RawDocument doc : fresh) {
            put(byKey, doc);
        }
        return new ArrayList<>(byKey.values());
    }

    /** 清理断点文件（整源完成或需要重抓时调用）。 */
    public static void clearPartial(String source) {
        deleteQuietly(docsFile(source));
        deleteQuietly(urlsFile(source));
    }

    private static void put(Map<String, RawDocument> byKey, RawDocument doc) {
        if (doc == null) {
            return;
        }
        String key = doc.url() != null && !doc.url().isBlank() ? doc.url() : doc.docKey();
        if (key != null) {
            byKey.put(key, doc);
        }
    }

    private static Map<String, SourceState> readState() {
        Path file = stateFile();
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, SourceState> state = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, SourceState>>() {
                    });
            return state == null ? new LinkedHashMap<>() : new LinkedHashMap<>(state);
        } catch (Exception e) {
            log.warn("断点状态读取失败，按全新爬取处理 : {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static void writeState(String source, String status, int docs) {
        Map<String, SourceState> state = readState();
        state.put(source, new SourceState(status, docs, Instant.now().toString()));
        Path file = stateFile();
        try {
            Files.createDirectories(baseDir);
            Path tmp = baseDir.resolve(STATE_FILE + ".tmp");
            Files.writeString(tmp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(state),
                    StandardCharsets.UTF_8);
            moveAtomically(tmp, file);
        } catch (Exception e) {
            log.warn("断点状态写入失败 source={} : {}", source, e.getMessage());
        }
    }

    /** 同目录原子替换，避免进程被杀时留下半截 JSON。 */
    private static void moveAtomically(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("断点文件删除失败 {} : {}", file, e.getMessage());
        }
    }

    private static Path stateFile() {
        return baseDir.resolve(STATE_FILE);
    }

    private static Path partialDir() {
        return baseDir.resolve(PARTIAL_DIR);
    }

    private static Path docsFile(String source) {
        return partialDir().resolve(source + ".jsonl");
    }

    private static Path urlsFile(String source) {
        return partialDir().resolve(source + ".urls");
    }
}
