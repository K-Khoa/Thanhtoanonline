package com.example.shop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gửi mail qua HTTPS API của Brevo (Render gói free chặn SMTP).
 * Cần biến môi trường: BREVO_API_KEY và MAIL_FROM (email người gửi đã xác minh trên Brevo).
 */
public final class EmailService {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mail-sender");
        t.setDaemon(true);
        return t;
    });

    public static void sendPaymentSuccess(OrderStore.Order o, String vnpTransactionNo) {
        POOL.submit(() -> {
            try {
                send(o, vnpTransactionNo);
            } catch (Exception e) {
                e.printStackTrace(); // TODO: ghi log / thử gửi lại
            }
        });
    }

    private static void send(OrderStore.Order o, String transNo) throws Exception {
        String money = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(o.amount());
        String html = "<div style='font-family:Arial,sans-serif;max-width:520px'>"
                + "<h2>Thanh toán thành công</h2>"
                + "<p>Xin chào <b>" + esc(o.name()) + "</b>, chúng tôi đã nhận được thanh toán của bạn.</p>"
                + "<table cellpadding='6'>"
                + "<tr><td>Mã đơn hàng</td><td><b>" + esc(o.txnRef()) + "</b></td></tr>"
                + "<tr><td colspan='2'>" + OrderStore.itemsHtml(o) + "</td></tr>"
                + "<tr><td>Số tiền</td><td><b>" + money + " VNĐ</b></td></tr>"
                + "<tr><td>Mã giao dịch</td><td>" + esc(transNo) + "</td></tr>"
                + "</table><p>Cảm ơn bạn đã mua hàng!</p></div>";

        String json = "{\"sender\":{\"name\":\"Music Store\",\"email\":" + q(env("MAIL_FROM")) + "},"
                + "\"to\":[{\"email\":" + q(o.email()) + ",\"name\":" + q(o.name()) + "}],"
                + "\"subject\":" + q("Xác nhận thanh toán đơn hàng " + o.txnRef()) + ","
                + "\"htmlContent\":" + q(html) + "}";

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                .timeout(Duration.ofSeconds(15))
                .header("api-key", env("BREVO_API_KEY"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 300) throw new IllegalStateException("Brevo " + r.statusCode() + ": " + r.body());
    }

    private static String env(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) throw new IllegalStateException("Thiếu biến môi trường " + key);
        return v;
    }

    private static String q(String s) { // chuỗi JSON
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
