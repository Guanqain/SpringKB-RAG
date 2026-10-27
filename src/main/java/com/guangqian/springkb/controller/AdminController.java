package com.guangqian.springkb.controller;

import java.util.List;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.ActiveRequest;
import com.guangqian.springkb.dto.AdminOverview;
import com.guangqian.springkb.dto.CategoryCreateRequest;
import com.guangqian.springkb.dto.CategoryDto;
import com.guangqian.springkb.dto.DocumentDto;
import com.guangqian.springkb.dto.ImportRequest;
import com.guangqian.springkb.dto.MemberDto;
import com.guangqian.springkb.dto.MetaUpdateRequest;
import com.guangqian.springkb.dto.PageResult;
import com.guangqian.springkb.dto.ReviewActionRequest;
import com.guangqian.springkb.dto.ReviewBatchRequest;
import com.guangqian.springkb.dto.RoleUpdateRequest;
import com.guangqian.springkb.dto.ScanRuleRequest;
import com.guangqian.springkb.kb.ScanAuditService;
import com.guangqian.springkb.kb.ScanRuleService;
import com.guangqian.springkb.kb.SensitiveScanException;
import com.guangqian.springkb.manage.AdminService;
import com.guangqian.springkb.manage.ReviewService;
import com.guangqian.springkb.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口（需 ADMIN 角色，由 SecurityConfig 的 hasRole('ADMIN') 保证）：
 * 文档列表（技术分类/来源/版本/状态/关键词过滤）、导入、元数据编辑、状态切换、删除、chunk 列表、
 * 技术分类维护、成员与角色管理。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ReviewService reviewService;
    private final ScanRuleService scanRuleService;
    private final ScanAuditService scanAuditService;

    public AdminController(AdminService adminService, ReviewService reviewService,
                           ScanRuleService scanRuleService, ScanAuditService scanAuditService) {
        this.adminService = adminService;
        this.reviewService = reviewService;
        this.scanRuleService = scanRuleService;
        this.scanAuditService = scanAuditService;
    }

    /** 文档列表：product/source/version/active/keyword 过滤，始终服务端分页（page 默认 1、size 默认 20，上限 200）。 */
    @GetMapping("/documents")
    public ApiResponse<PageResult<DocumentDto>> listDocuments(
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ApiResponse.ok(adminService.page(product, source, version, active, keyword, page, size));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 概览：五域聚合（内容资产 / 审核合规 / 问答检索 / 用户互动 / 系统健康），days 仅接受 7 / 30。 */
    @GetMapping("/overview")
    public ApiResponse<AdminOverview> overview(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(adminService.overview(days));
    }

    /** 导入：请求体为单文档表单（§5.3.1）；空请求体 → 从 data/raw 批量导入。 */
    @PostMapping("/documents/import")
    public ApiResponse<?> importDocument(@RequestBody(required = false) ImportRequest req) {
        try {
            if (req == null) {
                return ApiResponse.ok(adminService.importFromDisk());
            }
            return ApiResponse.ok(adminService.importOne(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 编辑文档元数据：技术分类 / 版本 / 可见性（同步向量 metadata）。 */
    @PatchMapping("/documents/{id}/meta")
    public ApiResponse<?> updateMeta(@PathVariable String id, @RequestBody(required = false) MetaUpdateRequest body,
            Authentication authentication) {
        try {
            return ApiResponse.ok(adminService.updateMeta(id, body, CurrentUser.require(authentication)));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (SensitiveScanException e) {
            return new ApiResponse<>(422, e.getMessage(), e.result());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 版本状态切换：{ "active": false }，kb_document + vector_store metadata 双写。 */
    @PatchMapping("/documents/{id}/active")
    public ApiResponse<?> setDocumentActive(@PathVariable String id, @RequestBody(required = false) ActiveRequest body) {
        try {
            return ApiResponse.ok(adminService.setActive(id, body == null ? null : body.active()));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 删除文档及其全部 chunk 与向量。 */
    @DeleteMapping("/documents/{id}")
    public ApiResponse<?> deleteDocument(@PathVariable String id) {
        try {
            adminService.delete(id);
            return ApiResponse.ok();
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 文档 chunk 列表（引用溯源调试）。 */
    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<?> listDocumentChunks(@PathVariable String id) {
        try {
            return ApiResponse.ok(adminService.listChunks(id));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 技术分类列表（含各自版本，供文档管理级联筛选）。 */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryDto>> categories() {
        return ApiResponse.ok(adminService.categories());
    }

    /** 新增 / 重命名技术分类。 */
    @PostMapping("/categories")
    public ApiResponse<?> addCategory(@RequestBody(required = false) CategoryCreateRequest req) {
        try {
            adminService.addCategory(req);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 成员列表（不含密码哈希）。 */
    @GetMapping("/members")
    public ApiResponse<List<MemberDto>> members() {
        return ApiResponse.ok(adminService.members());
    }

    /** 修改成员角色：ADMIN / MEMBER；禁止修改自己、禁止降级最后一名管理员。 */
    @PatchMapping("/members/{id}/role")
    public ApiResponse<MemberDto> updateMemberRole(@PathVariable long id,
            @RequestBody(required = false) RoleUpdateRequest body,
            Authentication authentication) {
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(adminService.updateRole(id, body.role(), CurrentUser.require(authentication)));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 内容审核队列：status 默认 pending，可选 approved / rejected；支持技术分类 / 作者 / 关键词筛选。 */
    @GetMapping("/reviews")
    public ApiResponse<?> listReviews(@RequestParam(required = false) String status,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String keyword) {
        try {
            return ApiResponse.ok(reviewService.list(status, product, author, keyword));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 批量通过：驳回需逐条填写理由，故不提供批量驳回。 */
    @PostMapping("/reviews/batch-approve")
    public ApiResponse<?> batchApprove(@RequestBody(required = false) ReviewBatchRequest body,
            Authentication authentication) {
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(reviewService.batchApprove(body.ids(), CurrentUser.require(authentication)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 审核通过：文档进入知识库并可被问答引用。reason 可选（通过备注）。 */
    @PostMapping("/reviews/{id}/approve")
    public ApiResponse<?> approveReview(@PathVariable String id,
            @RequestBody(required = false) ReviewActionRequest body,
            Authentication authentication) {
        try {
            reviewService.approve(id, CurrentUser.require(authentication), body == null ? null : body.note());
            return ApiResponse.ok();
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 审核驳回：必须填写理由（作者可见），驳回文档仅作者可见。 */
    @PostMapping("/reviews/{id}/reject")
    public ApiResponse<?> rejectReview(@PathVariable String id,
            @RequestBody(required = false) ReviewActionRequest body,
            Authentication authentication) {
        try {
            reviewService.reject(id, CurrentUser.require(authentication), body == null ? null : body.note());
            return ApiResponse.ok();
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ---------- 脱敏规则与拦截留痕 ----------

    /** 脱敏规则列表（内置 + 自定义，含启用状态）。 */
    @GetMapping("/scan-rules")
    public ApiResponse<?> listScanRules() {
        return ApiResponse.ok(scanRuleService.list());
    }

    /** 新增自定义规则（如工号 / 内网 IP 段 / 内部域名）。 */
    @PostMapping("/scan-rules")
    public ApiResponse<?> createScanRule(@RequestBody(required = false) ScanRuleRequest body) {
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(scanRuleService.create(body.label(), body.pattern()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 修改规则：开关 / 标签（自定义规则还可改正则）。 */
    @PatchMapping("/scan-rules/{code}")
    public ApiResponse<?> updateScanRule(@PathVariable String code,
            @RequestBody(required = false) ScanRuleRequest body) {
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(scanRuleService.update(code, body.label(), body.pattern(), body.enabled()));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 删除自定义规则（内置规则只能关闭）。 */
    @DeleteMapping("/scan-rules/{code}")
    public ApiResponse<?> deleteScanRule(@PathVariable String code) {
        try {
            scanRuleService.delete(code);
            return ApiResponse.ok();
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 脱敏拦截留痕（只含规则类型与命中数量，不含明文）。 */
    @GetMapping("/scan-logs")
    public ApiResponse<?> scanLogs(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(scanAuditService.recent(limit == null ? 100 : limit));
    }
}
