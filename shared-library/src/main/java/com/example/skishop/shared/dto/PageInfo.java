package com.example.skishop.shared.dto;

/**
 * ページング情報 DTO。
 *
 * @param page          現在のページ番号 (0-based)
 * @param size          1 ページあたりの件数
 * @param totalElements 総件数
 * @param totalPages    総ページ数
 */
public record PageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
