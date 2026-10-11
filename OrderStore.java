package com.example.shop;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** DEMO lưu trong RAM. Thực tế: thay bằng bảng orders trong database (mất dữ liệu khi Tomcat restart). */
public final class OrderStore {
    public record Order(String txnRef, String email, String name, long amount, String info) {}

    private static final Map<String, Order> ORDERS = new ConcurrentHashMap<>();
    private static final Set<String> PAID = ConcurrentHashMap.newKeySet();

    public static void save(Order o) { ORDERS.put(o.txnRef(), o); }
    public static Order find(String txnRef) { return ORDERS.get(txnRef); }

    /** true nếu đây là LẦN ĐẦU đơn được đánh dấu đã thanh toán (tránh gửi mail trùng giữa Return và IPN). */
    public static boolean markPaid(String txnRef) { return PAID.add(txnRef); }
}
