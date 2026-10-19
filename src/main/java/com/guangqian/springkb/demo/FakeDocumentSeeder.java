package com.guangqian.springkb.demo;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 第 1 周 Day2：两段假文档（official / community）入库，用于跑通链路。
 */
@Component
public class FakeDocumentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FakeDocumentSeeder.class);

    static final String OFFICIAL_ID = "fake-doc-official-cds-33";
    static final String COMMUNITY_ID = "fake-doc-community-cds-33";

    private final VectorStore vectorStore;

    public FakeDocumentSeeder(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Document> existing = this.vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Spring Boot CDS Class Data Sharing")
                        .topK(2)
                        .similarityThreshold(0.0d)
                        .filterExpression("demo == 'true'")
                        .build());

        if (!existing.isEmpty()) {
            log.info("Demo documents already present ({} hits), skip seeding", existing.size());
            return;
        }

        Document official = Document.builder()
                .id(OFFICIAL_ID)
                .text("""
                        Spring Boot 3.3 Official Documentation — Class Data Sharing (CDS)

                        Spring Boot 3.3 improves support for Application CDS.
                        To enable CDS training, use:
                        java -XX:ArchiveClassesAtExit=application.jsa -jar your-app.jar

                        To launch with the archive:
                        java -XX:SharedArchiveFile=application.jsa -jar your-app.jar

                        Official guidance emphasizes verifying the archive with -Xshare:on
                        and measuring startup time improvements on JDK 17+.
                        This content is the authoritative baseline for Spring Boot 3.3 CDS.
                        """)
                .metadata(Map.of(
                        "source", "official",
                        "version", "3.3",
                        "title", "Spring Boot 3.3 CDS",
                        "section", "deployment/cds",
                        "demo", "true",
                        "active", "true"))
                .build();

        Document community = Document.builder()
                .id(COMMUNITY_ID)
                .text("""
                        Stack Overflow (community, score=42) — How do I speed up Spring Boot 3.3 startup with CDS?

                        Top answer: generate a CDS archive once, then reuse it.
                        Example that worked for me on Spring Boot 3.3:
                        java -XX:ArchiveClassesAtExit=app.jsa -jar app.jar
                        java -XX:SharedArchiveFile=app.jsa -jar app.jar

                        Some users also add -Xlog:cds=debug while troubleshooting.
                        Note: this is a community workaround write-up, not official docs.
                        """)
                .metadata(Map.of(
                        "source", "community",
                        "version", "3.3",
                        "title", "SO: Spring Boot 3.3 CDS startup",
                        "so_score", "42",
                        "demo", "true",
                        "active", "true"))
                .build();

        this.vectorStore.add(List.of(official, community));
        log.info("Seeded 2 fake documents: official={}, community={}", OFFICIAL_ID, COMMUNITY_ID);
    }
}
