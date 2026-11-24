package com.example.chat.auth;

import org.springframework.http.HttpStatus;

/**
 * 鉴权失败。带 HTTP 状态码，控制器直接映射，不靠异常类型猜。
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static AuthException badRequest(String msg) {
        return new AuthException(HttpStatus.BAD_REQUEST, msg);
    }

    public static AuthException unauthorized(String msg) {
        return new AuthException(HttpStatus.UNAUTHORIZED, msg);
    }

    public static AuthException forbidden(String msg) {
        return new AuthException(HttpStatus.FORBIDDEN, msg);
    }
}
