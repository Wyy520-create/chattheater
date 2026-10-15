package com.chattheater.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_usage")
public class LlmUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long personaId;

    private String model;

    private Integer mock;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private Integer ok;

    private LocalDateTime createdAt;
}
