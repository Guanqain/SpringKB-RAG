package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端接口（暂为桩实现：先让前端管理页可正常加载，核心能力后续实现）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/documents")
    public ApiResponse<List<?>> listDocuments() {
        return ApiResponse.ok(List.of());
    }

    @PostMapping("/documents/import")
    public ApiResponse<?> importDocument() {
        return ApiResponse.ok(null);
    }

    @PatchMapping("/documents/{id}/active")
    public ApiResponse<?> setDocumentActive(@PathVariable String id) {
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<?> deleteDocument(@PathVariable String id) {
        return ApiResponse.ok(null);
    }
}
