package com.guangqian.springkb.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 Spring AI 官方 RAG 文档：ChatClient + QuestionAnswerAdvisor。
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html">RAG Reference</a>
 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(3)
                        .similarityThreshold(0.4d)
                        .build())
                .build();

        return builder
                .defaultSystem("""
                        你是 Spring 知识库助手。只根据提供的上下文回答问题。
                        回答时标明信息更可能来自 official（官方）还是 community（社区）。
                        若上下文不足以回答，明确说不知道。
                        """)
                .defaultAdvisors(qaAdvisor)
                .build();
    }
}
