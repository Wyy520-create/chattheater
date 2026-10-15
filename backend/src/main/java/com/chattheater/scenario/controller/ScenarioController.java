package com.chattheater.scenario.controller;

import com.chattheater.common.ApiResult;
import com.chattheater.scenario.entity.Scenario;
import com.chattheater.scenario.service.ScenarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    public ApiResult<List<Scenario>> list() {
        return ApiResult.ok(scenarioService.list());
    }

    @GetMapping("/{id}")
    public ApiResult<Scenario> detail(@PathVariable Long id) {
        return ApiResult.ok(scenarioService.detail(id));
    }
}
