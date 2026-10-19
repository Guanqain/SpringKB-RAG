package com.guangqian.springkb.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示接口：检索 + 问答（依赖启动时 FakeDocumentSeeder 入库）。
 */
@RestController
@RequestMapping("/api/demo")
public class RagDemoController {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagDemoController(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam(defaultValue = "How to configure CDS in Spring Boot 3.3?") String q,
            @RequestParam(defaultValue = "3") int topK) {
        List<Document> hits = this.vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(q)
                        .topK(topK)
                        .similarityThreshold(0.0d)
                        .build());

        return hits.stream().map(doc -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", doc.getId());
            row.put("score", doc.getScore());
            row.put("metadata", doc.getMetadata());
            row.put("text", truncate(doc.getText(), 180));
            return row;
        }).toList();
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody AskRequest request) {
        String question = request.question() == null || request.question().isBlank()
                ? "CDS 在 Spring Boot 3.3 中如何配置？"
                : request.question();

        String answer = this.chatClient.prompt()
                .user(question)
                .call()
                .content();

        return Map.of(
                "question", question,
                "answer", answer == null ? "" : answer);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    public record AskRequest(String question) {
    }
}
