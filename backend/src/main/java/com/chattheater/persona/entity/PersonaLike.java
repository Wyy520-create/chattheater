package com.chattheater.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("persona_like")
public class PersonaLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long personaId;
}
