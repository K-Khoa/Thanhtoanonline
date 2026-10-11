package com.example.shop;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.regex.Pattern;

/** Nhận form ở index.html, lưu đơn (kèm email khách) rồi chuyển sang trang thanh toán giả lập. */
@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        String name = t(req.getParameter("name"));
        String email = t(req.getParameter("email")); // TODO: nếu có đăng nhập, lấy email từ tài khoản
        String info = t(req.getParameter("info"));

        long amount;
        try { amount = Long.parseLong(t(req.getParameter("amount"))); } catch (NumberFormatException e) { amount = -1; }
        if (name.isEmpty() || !EMAIL.matcher(email).matches() || amount < 1000 || amount > 999_999_999L) {
            resp.setStatus(400);
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<h3>Thông tin không hợp lệ (cần tên, email đúng định dạng, số tiền ≥ 1.000đ).</h3>"
                    + "<a href='javascript:history.back()'>Quay lại</a>");
            return;
        }

        String ref = "DH" + System.currentTimeMillis();
        if (info.isEmpty()) info = "Thanh toán đơn hàng " + ref;
        OrderStore.save(new OrderStore.Order(ref, email, name, amount, info));
        resp.sendRedirect(req.getContextPath() + "/pay?ref=" + ref);
    }

    private static String t(String s) { return s == null ? "" : s.trim(); }
}
