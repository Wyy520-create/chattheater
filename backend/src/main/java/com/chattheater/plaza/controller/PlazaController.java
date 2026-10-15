package com.chattheater.plaza.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chattheater.common.ApiResult;
import com.chattheater.plaza.dto.PlazaDtos.HallVO;
import com.chattheater.plaza.dto.PlazaDtos.PlazaCardVO;
import com.chattheater.plaza.dto.PlazaDtos.StatsVO;
import com.chattheater.plaza.service.PlazaService;
import com.chattheater.security.UserContext;
import com.chattheater.theater.dto.TheaterDtos.TheaterVO;
import com.chattheater.theater.service.TheaterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 广场（只读，游客可访问）：直播大厅 / 名场面馆 / 统计。 */
@RestController
@RequestMapping("/api/plaza")
public class PlazaController {

    private final PlazaService plazaService;
    private final TheaterService theaterService;

    public PlazaController(PlazaService plazaService, TheaterService theaterService) {
        this.plazaService = plazaService;
        this.theaterService = theaterService;
    }

    @GetMapping("/live")
    public ApiResult<Page<PlazaCardVO>> live(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "12") int size) {
        return ApiResult.ok(plazaService.live(page, size));
    }

    @GetMapping("/ended")
    public ApiResult<Page<PlazaCardVO>> ended(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        return ApiResult.ok(plazaService.ended(page, size));
    }

    /** 剧目详情（对齐 TheaterService 的越权矩阵，游客仅见 PUBLIC）。 */
    @GetMapping("/{id}")
    public ApiResult<TheaterVO> detail(@PathVariable Long id) {
        return ApiResult.ok(theaterService.detail(id, UserContext.get()));
    }

    /** 名场面馆：完结剧目的高光回放。 */
    @GetMapping("/{id}/highlights")
    public ApiResult<HallVO> highlights(@PathVariable Long id) {
        return ApiResult.ok(plazaService.hall(id));
    }

    @GetMapping("/stats")
    public ApiResult<StatsVO> stats() {
        return ApiResult.ok(plazaService.stats());
    }
}
