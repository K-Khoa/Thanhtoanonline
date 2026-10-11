package com.example.shop;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Nhận giỏ hàng (dạng "id:sl,id:sl") + tên + email. Server tự tính tiền từ Catalog rồi chuyển sang trang thanh toán giả. */
@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        String name = t(req.getParameter("name"));
        String email = t(req.getParameter("email")); // TODO: nếu có đăng nhập, lấy email từ tài khoản

        List<OrderStore.Line> lines = new ArrayList<>();
        long total = 0;
        try {
            for (String part : t(req.getParameter("cart")).split(",")) {
                if (part.isBlank()) continue;
                String[] kv = part.split(":");
                Catalog.Product p = Catalog.find(Integer.parseInt(kv[0].trim()));
                int qty = Integer.parseInt(kv[1].trim());
                if (p == null || qty < 1 || qty > 99) throw new IllegalArgumentException();
                lines.add(new OrderStore.Line(p.name(), p.price(), qty));
                total += p.price() * qty;
            }
        } catch (RuntimeException e) {
            lines.clear();
        }

        if (name.isEmpty() || !EMAIL.matcher(email).matches() || lines.isEmpty()) {
            resp.setStatus(400);
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<h3>Thông tin không hợp lệ (cần giỏ hàng, họ tên và email đúng định dạng).</h3>"
                    + "<a href='javascript:history.back()'>Quay lại</a>");
            return;
        }

        String ref = "DH" + System.currentTimeMillis();
        OrderStore.save(new OrderStore.Order(ref, email, name, total, lines));
        resp.sendRedirect(req.getContextPath() + "/pay?ref=" + ref);
    }

    private static String t(String s) { return s == null ? "" : s.trim(); }
}
