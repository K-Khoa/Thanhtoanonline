package com.example.shop;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** GET /api/products -> JSON danh sách sản phẩm cho trang web. */
@WebServlet("/api/products")
public class ProductsServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        for (Catalog.Product p : Catalog.ALL) {
            if (sb.length() > 1) sb.append(',');
            sb.append("{\"id\":").append(p.id())
              .append(",\"name\":\"").append(p.name().replace("\"", "\\\"")).append("\"")
              .append(",\"price\":").append(p.price())
              .append(",\"icon\":\"").append(p.icon()).append("\"}");
        }
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(sb.append(']').toString());
    }
}
