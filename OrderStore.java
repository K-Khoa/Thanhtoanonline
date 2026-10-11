package com.example.shop;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** DEMO lưu trong RAM. Thực tế: thay bằng database (mất dữ liệu khi web restart). */
public final class OrderStore {
    public record Line(String name, long price, int qty) {}
    public record Order(String txnRef, String email, String name, long amount, List<Line> lines) {}

    private static final Map<String, Order> ORDERS = new ConcurrentHashMap<>();
    private static final Set<String> PAID = ConcurrentHashMap.newKeySet();

    public static void save(Order o) { ORDERS.put(o.txnRef(), o); }
    public static Order find(String txnRef) { return txnRef == null ? null : ORDERS.get(txnRef); }

    /** true nếu đây là LẦN ĐẦU đơn được đánh dấu đã thanh toán (tránh gửi mail trùng). */
    public static boolean markPaid(String txnRef) { return PAID.add(txnRef); }

    /** Bảng HTML các sản phẩm trong đơn, dùng cho trang thanh toán và email. */
    public static String itemsHtml(Order o) {
        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
        StringBuilder sb = new StringBuilder("<table width='100%' style='border-collapse:collapse'>");
        for (Line l : o.lines()) {
            sb.append("<tr><td>").append(esc(l.name())).append(" × ").append(l.qty())
              .append("</td><td align='right'>").append(nf.format(l.price() * l.qty())).append(" ₫</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
