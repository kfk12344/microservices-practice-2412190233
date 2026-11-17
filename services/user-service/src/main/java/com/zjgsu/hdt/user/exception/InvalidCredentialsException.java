package com.zjgsu.hdt.user.exception;

/**
 * 登录失败：用户名不存在或口令不匹配。
 *
 * <p>两种情况<strong>刻意返回同一个错误</strong>：如果"用户不存在"和"口令错误"能被区分，
 * 攻击者就可以用登录接口枚举系统里有哪些账号（用户枚举漏洞）。
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super("用户名或口令错误");
    }
}
