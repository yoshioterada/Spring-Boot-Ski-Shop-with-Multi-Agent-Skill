package com.example.skishop.shared.dto;

/**
 * ページネーションメタ情報レコード。
 *
 * @param page          現在のページ番号 (0 始まり)
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

    /**
     * ページネーション情報を生成するファクトリメソッド。
     *
     * @param page          ページ番号 (0 始まり)
     * @param size          1 ページあたりの件数
     * @param totalElements 総件数
     * @return PageInfo インスタンス
     */
    public static PageInfo of(int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageInfo(page, size, totalElements, totalPages);
    }

    /** 最初のページかどうか */
    public boolean isFirst() {
        return page == 0;
    }

    /** 最後のページかどうか */
    public boolean isLast() {
        return page >= totalPages - 1;
    }

    /** 次のページが存在するかどうか */
    public boolean hasNext() {
        return page < totalPages - 1;
    }

    /** 前のページが存在するかどうか */
    public boolean hasPrevious() {
        return page > 0;
    }
}
