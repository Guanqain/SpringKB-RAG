package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.ActiveRequest;
import com.guangqian.springkb.dto.ImportRequest;
import com.guangqian.springkb.manage.AdminService;
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
 * 管理端接口（Task 6，§5.3）：文档列表（分页/过滤）、单文档导入或 data/raw 批量导入、
 * 版本状态切换（active 双写）、删除、chunk 列表。鉴权由 SecurityConfig 保证（未登录 401）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 文档列表：source/version/active/keyword 过滤；page+size 同时给出时分页返回，否则全量数组。 */
    @GetMapping("/documents")
    public ApiResponse<?> listDocuments(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        try {
            if (page != null || size != null) {
                int p = page == null ? 1 : page;
                int s = size == null ? 20 : size;
                return ApiResponse.ok(adminService.page(source, version, active, keyword, p, s));
            }
            return ApiResponse.ok(adminService.listAll(source, version, active, keyword));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
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
}
