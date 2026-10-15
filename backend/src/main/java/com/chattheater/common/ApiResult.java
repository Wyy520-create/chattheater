package com.chattheater.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一响应包装：{code, msg, data}，code 与 HTTP 状态码一致，成功为 0。
 */
@Data
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null);
    }

    public static <T> ApiResult<T> error(int code, String msg) {
        return new ApiResult<>(code, msg, null);
    }
}
