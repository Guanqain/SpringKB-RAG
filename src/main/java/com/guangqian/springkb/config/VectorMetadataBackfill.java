package com.guangqian.springkb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时补齐历史向量 metadata 的 visibility / review_status 字段。
 *
 * <p>可见性与审核状态在检索层下推为 {@code visibility == 'team' && review_status == 'approved'}；
 * 历史向量没有这些键会被整体排除，因此启动时把缺失的行回填为 team / approved
 * （与 kb_document 的默认值保持一致）。</p>
 *
 * <p>vector_store 表由 Spring AI 初始化，可能晚于 schema.sql 或尚未建表，故容错处理、失败只告警不阻断启动。</p>
 */
@Component
public class VectorMetadataBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorMetadataBackfill.class);

    private final JdbcTemplate jdbcTemplate;

    public VectorMetadataBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = jdbcTemplate.update("""
                    UPDATE vector_store
                    SET metadata = jsonb_set(
                        jsonb_set(metadata::jsonb, '{visibility}',
                                  COALESCE(metadata::jsonb -> 'visibility', '"team"'::jsonb)),
                        '{review_status}', '"approved"'::jsonb)
                    WHERE metadata ->> 'visibility' IS NULL OR metadata ->> 'review_status' IS NULL
                    """);
            if (n > 0) {
                log.info("向量 metadata 回填 visibility=team / review_status=approved 共 {} 行", n);
            }
        } catch (Exception e) {
            log.warn("向量 metadata 回填跳过：{}", e.getMessage());
        }
    }
}
