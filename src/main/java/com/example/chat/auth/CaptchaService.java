package com.example.chat.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片验证码。
 *
 * <p>为什么不引第三方库：验证码就是「画一张图 + 记一个答案」，用 JDK 自带的
 * BufferedImage / Graphics2D 三十行就能画完，多引一个依赖反而多一份维护成本。
 *
 * <p>三个必须做到的点：
 * <ol>
 *   <li><b>一次性</b>：校验通过或失败都立刻删掉，不能拿同一个码反复试。</li>
 *   <li><b>有有效期</b>：默认 2 分钟，过期即失效，防止先批量取码再慢慢撞。</li>
 *   <li><b>服务端存答案</b>：图片里只有像素，答案在服务端内存里，
 *       前端拿不到正确答案（这点和 token 一样，别把答案回传给前端）。</li>
 * </ol>
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 去掉了 0O1Il 这类容易看错的字符，减少"我明明输对了"的投诉 */
    private static final String CHARS = "34679ACDEFGHJKMNPQRTUVWXY";
    private static final int CODE_LENGTH = 4;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final AuthProperties props;

    /** key = captchaId，value = 答案 + 过期时间；单实例内存态，多实例要换 Redis */
    private final Map<String, Captcha> store = new ConcurrentHashMap<>();

    public CaptchaService(AuthProperties props) {
        this.props = props;
    }

    public record CaptchaIssue(String captchaId, String imageDataUrl, int expireSeconds) {
    }

    private record Captcha(String code, Instant expireAt) {
    }

    /** 生成一张验证码：返回 id 与可直接放进 img src 的 data URL */
    public CaptchaIssue issue() {
        purgeExpired();

        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        int ttl = props.getCaptchaTtlSeconds();
        store.put(captchaId, new Captcha(code, Instant.now().plusSeconds(ttl)));

        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(render(code));
        return new CaptchaIssue(captchaId, dataUrl, ttl);
    }

    /**
     * 校验并消费验证码。无论对错都把这条删掉——否则攻击者可以拿同一张图反复试，
     * 一次生成、暴力猜四次字符，验证码就白加了。
     */
    public boolean verify(String captchaId, String input) {
        if (captchaId == null || captchaId.isBlank() || input == null || input.isBlank()) {
            return false;
        }
        Captcha captcha = store.remove(captchaId);
        if (captcha == null) {
            return false;
        }
        if (captcha.expireAt().isBefore(Instant.now())) {
            return false;
        }
        return captcha.code().equalsIgnoreCase(input.trim());
    }

    private void purgeExpired() {
        if (store.size() > 10000) {          // 兜底：防止被刷爆内存
            store.clear();
            log.warn("验证码表超过 10000 条，已整体清空");
            return;
        }
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(now));
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    // ===== 画图 =====

    private static byte[] render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 底色：浅灰白，和登录卡片背景接近
            g.setColor(new Color(0xF7, 0xF8, 0xFA));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 干扰线：6 条淡色斜线，挡住简单的图像切割
            g.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(150, 215));
                g.drawLine(rand(WIDTH), rand(HEIGHT), rand(WIDTH), rand(HEIGHT));
            }

            // 字符：逐个轻微旋转 + 上下抖动
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                double angle = (RANDOM.nextDouble() - 0.5) * 0.5;   // ±14°
                double pivotX = 16 + i * 24.0;
                g.rotate(angle, pivotX, HEIGHT / 2.0);
                g.setColor(randomColor(30, 110));
                g.drawString(String.valueOf(code.charAt(i)),
                        (float) pivotX - 8, 28 + RANDOM.nextInt(5) - 2);
                g.rotate(-angle, pivotX, HEIGHT / 2.0);
            }

            // 噪点
            for (int i = 0; i < 40; i++) {
                image.setRGB(rand(WIDTH), rand(HEIGHT), randomColor(110, 200).getRGB());
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        }
    }

    private static int rand(int bound) {
        return RANDOM.nextInt(bound);
    }

    private static Color randomColor(int min, int max) {
        int span = max - min;
        return new Color(min + RANDOM.nextInt(span), min + RANDOM.nextInt(span), min + RANDOM.nextInt(span));
    }

    /** 仅用于测试：判断某个 id 是否还在（一次性的验证） */
    boolean contains(String captchaId) {
        return store.containsKey(captchaId);
    }

    /** 仅用于测试：取答案，正式代码不应调用 */
    String codeOf(String captchaId) {
        Captcha captcha = store.get(captchaId);
        return captcha == null ? null : captcha.code();
    }

    /** 仅用于测试：直接塞一条已过期的记录 */
    void putExpired(String captchaId, String code) {
        store.put(captchaId, new Captcha(code, Instant.now().minus(Duration.ofSeconds(1))));
    }
}
