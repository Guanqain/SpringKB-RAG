package com.guangqian.springkb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理端全系统状态概览（GET /api/admin/overview?days=7|30）。
 *
 * <p>只包含聚合值：cards（核心指标卡）、sections（五个数据域）、trend（近 N 天趋势）、
 * todos（待办条）、meta（时间窗与生成时间）。不包含文档行、正文或脱敏命中明文。</p>
 */
public record AdminOverview(
        Cards cards,
        Sections sections,
        TrendSeries trend,
        TodoSummary todos,
        Meta meta) {

    /** 顶部 8 张核心指标卡。 */
    public record Cards(
            long totalDocs,
            long chunks,
            double activeRate,
            long pendingReview,
            long windowQa,
            double foundRate,
            long users,
            long windowNewDocs) {
    }

    /** 五个数据域。 */
    public record Sections(
            ContentStats content,
            GovernanceStats governance,
            QaStats qa,
            UserStats users,
            EngagementStats engagement,
            HealthStats health) {
    }

    // ---------- A. 内容资产 ----------

    public record ContentStats(
            long totalDocs,
            long activeDocs,
            long inactiveDocs,
            long chunks,
            double avgChunksPerDoc,
            long emptyChunkDocs,
            List<ProductSlice> products,
            OtherBucket otherProducts,
            List<ProductSlice> origins,
            OtherBucket otherOrigins,
            List<ProductSlice> docTypes,
            List<ProductSlice> languages,
            List<ProductSlice> versions,
            OtherBucket otherVersions,
            VisibilityStats visibility,
            FreshnessStats freshness,
            double avgAgeDays,
            long todayNewDocs,
            long windowNewDocs,
            long prevWindowNewDocs) {
    }

    /** 分组计数切片；product 字段承载维度值（product/origin/doc_type/language/version）。 */
    public record ProductSlice(String product, long count) {
    }

    /** Top N 之外的剩余项：count=剩余项数，total=剩余文档数之和。 */
    public record OtherBucket(long count, long total) {
    }

    /** 可见性分布；private 是 Java 关键字，序列化时仍输出为 "private"。 */
    public record VisibilityStats(long team, @JsonProperty("private") long privateCount) {
    }

    /** last_modified 距今分段；未知=NULL。 */
    public record FreshnessStats(
            long within30Days,
            long days31To180,
            long days181To365,
            long over365Days,
            long unknown) {
    }

    // ---------- B. 审核与合规 ----------

    public record GovernanceStats(
            List<ReviewStatusSlice> reviewStatus,
            double approvalRate,
            double avgReviewHours,
            double pendingOldestDays,
            long rejectedStale,
            ScanStats scan,
            long notificationsUnread) {
    }

    public record ReviewStatusSlice(String status, long count, double pct) {
    }

    public record ScanStats(
            long windowBlocks,
            long blockedTitles,
            long prevWindowBlocks,
            long hits,
            List<RuleHit> byRule,
            List<ActionSlice> byAction) {
    }

    public record RuleHit(String ruleType, long count) {
    }

    public record ActionSlice(String action, long count) {
    }

    // ---------- C. 问答与检索 ----------

    public record QaStats(
            long conversations,
            long messages,
            long windowQa,
            long prevWindowQa,
            double foundRate,
            double noAnswerRate,
            double avgSources,
            long activeAskers,
            List<BlindSpot> blindspotsTop) {
    }

    /** 知识盲区：只含问题原文（截断 60 字）与出现次数，不含用户名。 */
    public record BlindSpot(String question, long count) {
    }

    public record TrendPoint(String date, long newDocs, long qaCount) {
    }

    public record TrendSeries(int days, List<TrendPoint> points) {
    }

    // ---------- D. 用户与互动 ----------

    public record UserStats(
            long total,
            long admins,
            long members,
            long newInWindow,
            long prevNewInWindow,
            long activeInWindow,
            long prevActiveInWindow,
            List<ContributorSlice> topContributors,
            List<RoleSlice> roles) {
    }

    public record ContributorSlice(String username, long count) {
    }

    public record RoleSlice(String role, long count) {
    }

    public record EngagementStats(
            long likes,
            long favorites,
            List<FavoriteSlice> topFavorites) {
    }

    public record FavoriteSlice(String docId, String title, long count) {
    }

    // ---------- E. 系统健康 ----------

    public record HealthStats(
            long vectorRows,
            double coverageRate,
            long dbSizeBytes,
            String lastIngestedAt,
            String lastScanAt,
            String lastQaAt) {
    }

    // ---------- 待办与元信息 ----------

    public record TodoSummary(
            long pendingReview,
            long rejectedStale,
            long scanBlocks,
            long blindspots) {
    }

    public record Meta(int windowDays, String generatedAt) {
    }
}
