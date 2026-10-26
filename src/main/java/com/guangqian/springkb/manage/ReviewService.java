package com.guangqian.springkb.manage;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.ReviewItem;
import com.guangqian.springkb.me.NotificationService;
import com.guangqian.springkb.repository.UserRepository;
import com.guangqian.springkb.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UGC 内容审核：待审列表、通过（进入知识库与问答引用）、驳回（保持不可见并记录理由）。
 *
 * <p>只有 author 非空的 UGC 文档进入审核队列；官方/爬虫文档与历史数据默认 approved，不受影响。</p>
 * <p>自审约束：系统内存在多名管理员时，不允许审核自己发布的文档；只有一名管理员时放行，
 * 避免唯一管理员无法推进审核导致文档永久卡在 pending。</p>
 * <p>审核结果会写入站内通知（{@link NotificationService}），作者不必反复刷新「我的文档」。</p>
 */
@Service
public class ReviewService {

    /** 批量审核结果：approved 为成功数，errors 为逐条失败原因（含 docId）。 */
    public record BatchResult(int approved, int failed, List<String> errors) {
    }

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    private static final Set<String> STATUSES = Set.of(STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED);

    private static final RowMapper<ReviewItem> MAPPER = (rs, rowNum) -> new ReviewItem(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("product"),
            rs.getString("version"),
            rs.getString("author"),
            rs.getString("review_status"),
            rs.getString("review_note"),
            rs.getString("reviewed_by"),
            toIso(rs.getTimestamp("reviewed_at")),
            toIso(rs.getTimestamp("updated_at")),
            rs.getInt("chunk_count"));

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ReviewService(JdbcTemplate jdbcTemplate, UserRepository userRepository,
                         NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** 审核队列：默认 pending；支持 pending / rejected / approved。 */
    public List<ReviewItem> list(String status) {
        return list(status, null, null, null);
    }

    /**
     * 审核队列（筛选版）：status 必填语义（默认 pending），product / author / keyword 可选，
     * 供文档量上来后按技术分类或作者定位。
     */
    public List<ReviewItem> list(String status, String product, String author, String keyword) {
        String normalized = status == null || status.isBlank() ? STATUS_PENDING : status.trim().toLowerCase();
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("status 仅支持 pending / approved / rejected");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, product, version, author, review_status, review_note,
                       reviewed_by, reviewed_at, updated_at, chunk_count
                FROM kb_document
                WHERE author IS NOT NULL AND review_status = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(normalized);
        if (product != null && !product.isBlank()) {
            sql.append(" AND product = ?");
            args.add(product.trim());
        }
        if (author != null && !author.isBlank()) {
            sql.append(" AND author = ?");
            args.add(author.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title ILIKE ? OR author ILIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY updated_at DESC LIMIT 500");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    @Transactional
    public void approve(String docId, String reviewer, String note) {
        doApprove(docId, reviewer, note);
    }

    @Transactional
    public void reject(String docId, String reviewer, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("驳回时请填写理由（作者可见）");
        }
        Row row = requirePending(docId);
        checkSelfReview(row, reviewer);
        jdbcTemplate.update("""
                UPDATE kb_document
                SET review_status = 'rejected', review_note = ?, reviewed_by = ?, reviewed_at = now(),
                    updated_at = now()
                WHERE id = ?
                """, note.trim(), reviewer, docId);
        syncVector(docId, STATUS_REJECTED);
        notificationService.notifyReview(row.author(), docId, row.title(), false, note.trim());
        log.info("审核驳回 docId={} author={} reviewer={}", docId, row.author(), reviewer);
    }

    /**
     * 批量通过：逐条执行，单条失败（非待审 / 自审约束）只记录原因不中断其余文档。
     * 驳回仍需逐条填写理由，因此不提供批量驳回。
     */
    @Transactional
    public BatchResult batchApprove(List<String> docIds, String reviewer) {
        if (docIds == null || docIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要审核的文档");
        }
        int approved = 0;
        List<String> errors = new ArrayList<>();
        for (String docId : docIds) {
            if (docId == null || docId.isBlank()) {
                continue;
            }
            try {
                doApprove(docId.trim(), reviewer, null);
                approved++;
            } catch (NotFoundException | IllegalArgumentException e) {
                errors.add(docId + ": " + e.getMessage());
            }
        }
        return new BatchResult(approved, errors.size(), List.copyOf(errors));
    }

    private void doApprove(String docId, String reviewer, String note) {
        Row row = requirePending(docId);
        checkSelfReview(row, reviewer);
        jdbcTemplate.update("""
                UPDATE kb_document
                SET review_status = 'approved', review_note = ?, reviewed_by = ?, reviewed_at = now(),
                    updated_at = now()
                WHERE id = ?
                """, trimToNull(note), reviewer, docId);
        syncVector(docId, STATUS_APPROVED);
        notificationService.notifyReview(row.author(), docId, row.title(), true, null);
        log.info("审核通过 docId={} author={} reviewer={}", docId, row.author(), reviewer);
    }

    private Row requirePending(String docId) {
        List<Row> rows = jdbcTemplate.query(
                "SELECT author, review_status, title FROM kb_document WHERE id = ?",
                (rs, rowNum) -> new Row(rs.getString("author"), rs.getString("review_status"),
                        rs.getString("title")), docId);
        if (rows.isEmpty()) {
            throw new NotFoundException("文档不存在: " + docId);
        }
        Row row = rows.get(0);
        if (!STATUS_PENDING.equals(row.reviewStatus())) {
            throw new IllegalArgumentException("该文档当前状态不是待审核: " + row.reviewStatus());
        }
        return row;
    }

    private void checkSelfReview(Row row, String reviewer) {
        if (reviewer != null && reviewer.equals(row.author())
                && userRepository.countByRole(AuthService.ROLE_ADMIN) > 1) {
            throw new IllegalArgumentException("存在其他管理员时不能审核自己发布的文档");
        }
    }

    private void syncVector(String docId, String reviewStatus) {
        jdbcTemplate.update("""
                UPDATE vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{review_status}', to_jsonb(?::text))
                WHERE metadata ->> 'doc_id' = ?
                """, reviewStatus, docId);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String toIso(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate() + " " + ldt.toLocalTime().withNano(0);
    }

    private record Row(String author, String reviewStatus, String title) {
    }
}
