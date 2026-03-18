package com.example.skishop.inventory.repository.specification;

import com.example.skishop.inventory.model.Category;
import com.example.skishop.inventory.model.Product;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification パターンを使用した商品検索フィルタ。
 */
public class ProductSpecification {

    private ProductSpecification() {
    }

    /**
     * キーワードで商品名・説明・ブランドを検索する。
     */
    public static Specification<Product> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            var pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("brand")), pattern)
            );
        };
    }

    /**
     * カテゴリ ID でフィルタする。
     */
    public static Specification<Product> hasCategory(Long categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) {
                return cb.conjunction();
            }
            Join<Product, Category> categoryJoin = root.join("category", JoinType.INNER);
            return cb.equal(categoryJoin.get("id"), categoryId);
        };
    }

    /**
     * 価格範囲でフィルタする。
     */
    public static Specification<Product> hasPriceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * ブランドでフィルタする。
     */
    public static Specification<Product> hasBrand(String brand) {
        return (root, query, cb) -> {
            if (brand == null || brand.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("brand")), brand.toLowerCase());
        };
    }

    /**
     * アクティブな商品のみを返す。
     */
    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("isActive"));
    }

    /**
     * 複合検索条件を結合する。
     */
    public static Specification<Product> buildSearchSpec(
            String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, String brand) {
        return Specification.where(isActive())
                .and(hasKeyword(keyword))
                .and(hasCategory(categoryId))
                .and(hasPriceBetween(minPrice, maxPrice))
                .and(hasBrand(brand));
    }
}
