package com.example.shop;

import java.util.List;

/** Danh sách sản phẩm (nguồn giá duy nhất, server tự tính tiền). Sau này có thể thay bằng database. */
public final class Catalog {
    public record Product(int id, String name, long price, String icon) {}

    public static final List<Product> ALL = List.of(
        new Product(1, "Guitar acoustic Yamaha F310", 3_200_000, "🎸"),
        new Product(2, "Piano điện Casio CDP-S110", 9_500_000, "🎹"),
        new Product(3, "Trống cajon gỗ", 1_800_000, "🥁"),
        new Product(4, "Violin 4/4 cho người mới", 2_100_000, "🎻"),
        new Product(5, "Micro thu âm USB", 1_250_000, "🎤"),
        new Product(6, "Tai nghe kiểm âm", 1_490_000, "🎧"));

    public static Product find(int id) {
        for (Product p : ALL) if (p.id() == id) return p;
        return null;
    }
}
