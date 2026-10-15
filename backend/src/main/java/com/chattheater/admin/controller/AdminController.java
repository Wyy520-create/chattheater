package com.chattheater.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chattheater.admin.dto.AdminDtos.CostVO;
import com.chattheater.admin.dto.AdminDtos.OverviewVO;
import com.chattheater.admin.dto.AdminDtos.WordReq;
import com.chattheater.admin.service.AdminService;
import com.chattheater.common.ApiResult;
import com.chattheater.moderation.entity.ReviewLog;
import com.chattheater.moderation.entity.SensitiveWord;
import com.chattheater.persona.entity.Persona;
import com.chattheater.security.AdminGuard;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 运营端（仅 ADMIN）：看板 / 成本 / 敏感词 / 审核日志 / 人设管理。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ApiResult<OverviewVO> overview() {
        AdminGuard.check();
        return ApiResult.ok(adminService.overview());
    }

    @GetMapping("/cost")
    public ApiResult<CostVO> cost() {
        AdminGuard.check();
        return ApiResult.ok(adminService.cost());
    }

    @GetMapping("/review/words")
    public ApiResult<Page<SensitiveWord>> words(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        AdminGuard.check();
        return ApiResult.ok(adminService.words(page, size));
    }

    @PostMapping("/review/words")
    public ApiResult<SensitiveWord> addWord(@RequestBody WordReq req) {
        AdminGuard.check();
        return ApiResult.ok(adminService.addWord(req.getWord(), req.getCategory()));
    }

    @DeleteMapping("/review/words/{id}")
    public ApiResult<Void> deleteWord(@PathVariable Long id) {
        AdminGuard.check();
        adminService.deleteWord(id);
        return ApiResult.ok();
    }

    @GetMapping("/review/logs")
    public ApiResult<Page<ReviewLog>> logs(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        AdminGuard.check();
        return ApiResult.ok(adminService.logs(page, size));
    }

    @GetMapping("/review/personas")
    public ApiResult<Page<Persona>> personas(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        AdminGuard.check();
        return ApiResult.ok(adminService.personas(page, size, keyword, status));
    }

    @PutMapping("/review/personas/{id}/status")
    public ApiResult<Persona> personaStatus(@PathVariable Long id,
                                            @RequestBody Map<String, Integer> body) {
        AdminGuard.check();
        return ApiResult.ok(adminService.personaStatus(id, body.get("status")));
    }
}
