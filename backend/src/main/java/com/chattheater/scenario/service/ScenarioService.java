package com.chattheater.scenario.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chattheater.common.BizException;
import com.chattheater.scenario.entity.Scenario;
import com.chattheater.scenario.mapper.ScenarioMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScenarioService {

    private final ScenarioMapper scenarioMapper;

    public ScenarioService(ScenarioMapper scenarioMapper) {
        this.scenarioMapper = scenarioMapper;
    }

    public List<Scenario> list() {
        return scenarioMapper.selectList(new QueryWrapper<Scenario>().orderByAsc("id"));
    }

    public Scenario detail(Long id) {
        Scenario scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景剧本不存在");
        }
        return scenario;
    }
}
