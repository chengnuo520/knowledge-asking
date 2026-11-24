package com.example.chat.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证码的单元测试。重点验证三件事：能生成图、只能用一次、会过期。
 */
class CaptchaServiceTest {

    private CaptchaService service;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setCaptchaTtlSeconds(120);
        service = new CaptchaService(props);
    }

    @Test
    @DisplayName("生成验证码：返回 id 与可直接放进 img 的 PNG data URL")
    void issueReturnsImage() {
        CaptchaService.CaptchaIssue issue = service.issue();

        assertThat(issue.captchaId()).isNotBlank();
        assertThat(issue.imageDataUrl()).startsWith("data:image/png;base64,");
        assertThat(issue.imageDataUrl().length()).isGreaterThan(500);   // 不是空图
        assertThat(issue.expireSeconds()).isEqualTo(120);
        // 答案不能出现在返回内容里
        assertThat(issue.imageDataUrl()).doesNotContain(service.codeOf(issue.captchaId()));
    }

    @Test
    @DisplayName("答对了通过，但同一张只能通过一次")
    void verifyOnceOnly() {
        CaptchaService.CaptchaIssue issue = service.issue();
        String code = service.codeOf(issue.captchaId());

        assertThat(service.verify(issue.captchaId(), code)).isTrue();
        assertThat(service.verify(issue.captchaId(), code)).isFalse();   // 二次使用失败
        assertThat(service.contains(issue.captchaId())).isFalse();       // 已被消费
    }

    @Test
    @DisplayName("答错了也不给第二次机会，并且条目被删除")
    void wrongCodeConsumesEntry() {
        CaptchaService.CaptchaIssue issue = service.issue();
        String code = service.codeOf(issue.captchaId());
        String wrong = code.equals("AAAA") ? "BBBB" : "AAAA";

        assertThat(service.verify(issue.captchaId(), wrong)).isFalse();
        assertThat(service.contains(issue.captchaId())).isFalse();
        // 即便这时再输正确答案，也已经没有了
        assertThat(service.verify(issue.captchaId(), code)).isFalse();
    }

    @Test
    @DisplayName("不区分大小写，前后空格自动忽略")
    void caseInsensitiveAndTrimmed() {
        CaptchaService.CaptchaIssue issue = service.issue();
        String code = service.codeOf(issue.captchaId());

        assertThat(service.verify(issue.captchaId(), "  " + code.toLowerCase() + "  ")).isTrue();
    }

    @Test
    @DisplayName("过期后无效")
    void expiredRejected() {
        service.putExpired("expired-id", "ABCD");

        assertThat(service.verify("expired-id", "ABCD")).isFalse();
    }

    @Test
    @DisplayName("空值、不存在的 id 一律不通过")
    void blankRejected() {
        assertThat(service.verify(null, "ABCD")).isFalse();
        assertThat(service.verify("", "ABCD")).isFalse();
        assertThat(service.verify("no-such-id", "ABCD")).isFalse();

        CaptchaService.CaptchaIssue issue = service.issue();
        assertThat(service.verify(issue.captchaId(), "")).isFalse();
        assertThat(service.verify(issue.captchaId(), "   ")).isFalse();
    }
}
