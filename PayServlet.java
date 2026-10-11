package com.example.shop;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;

/** CỔNG THANH TOÁN GIẢ LẬP: không trừ tiền thật. Bấm "Thanh toán thành công" thì gửi mail THẬT cho khách. */
@WebServlet("/pay")
public class PayServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OrderStore.Order o = OrderStore.find(req.getParameter("ref"));
        if (o == null) { page(resp, "Không tìm thấy đơn", "<p>Đơn hàng không tồn tại hoặc đã hết hạn.</p>" + home(req)); return; }

        String money = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(o.amount());
        page(resp, "Cổng thanh toán giả lập",
            "<p style='background:#fff3cd;padding:8px;border-radius:6px'>⚠ Đây là thanh toán GIẢ LẬP, không trừ tiền thật.</p>"
          + "<table cellpadding='6'>"
          + "<tr><td>Mã đơn</td><td><b>" + esc(o.txnRef()) + "</b></td></tr>"
          + "<tr><td>Khách hàng</td><td>" + esc(o.name()) + "</td></tr>"
          + "<tr><td>Email nhận xác nhận</td><td>" + esc(o.email()) + "</td></tr>"
          + "<tr><td>Nội dung</td><td>" + esc(o.info()) + "</td></tr>"
          + "<tr><td>Số tiền</td><td><b>" + money + " VNĐ</b></td></tr></table>"
          + "<form method='post' action='pay'><input type='hidden' name='ref' value='" + esc(o.txnRef()) + "'>"
          + "<button name='action' value='success' style='background:#198754'>Thanh toán thành công</button> "
          + "<button name='action' value='cancel' style='background:#dc3545'>Hủy thanh toán</button></form>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OrderStore.Order o = OrderStore.find(req.getParameter("ref"));
        if (o == null) { page(resp, "Không tìm thấy đơn", "<p>Đơn hàng không tồn tại hoặc đã hết hạn.</p>" + home(req)); return; }

        if ("success".equals(req.getParameter("action"))) {
            if (OrderStore.markPaid(o.txnRef())) { // chỉ gửi mail 1 lần dù bấm nhiều lần
                String transNo = String.valueOf(10_000_000 + new Random().nextInt(90_000_000));
                EmailService.sendPaymentSuccess(o, transNo);
            }
            page(resp, "Thanh toán thành công",
                "<h2 style='color:#198754'>✔ Thanh toán thành công</h2>"
              + "<p>Email xác nhận đã được gửi tới <b>" + esc(o.email()) + "</b> (nhớ xem cả mục Spam).</p>" + home(req));
        } else {
            page(resp, "Đã hủy", "<h2 style='color:#dc3545'>Bạn đã hủy thanh toán.</h2><p>Không có email nào được gửi.</p>" + home(req));
        }
    }

    private static String home(HttpServletRequest req) { return "<a href='" + req.getContextPath() + "/'>Về trang đặt hàng</a>"; }

    private static void page(HttpServletResponse resp, String title, String body) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        resp.getWriter().write("<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>"
          + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>" + title + "</title>"
          + "<style>body{font-family:Arial,sans-serif;background:#f3f5f9;padding:24px}"
          + ".box{max-width:480px;margin:auto;background:#fff;padding:24px;border-radius:12px;box-shadow:0 2px 12px #0001}"
          + "button{padding:10px 16px;border:0;border-radius:8px;color:#fff;font-size:15px;cursor:pointer;margin-top:12px}</style></head>"
          + "<body><div class='box'><h2>" + title + "</h2>" + body + "</div></body></html>");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
