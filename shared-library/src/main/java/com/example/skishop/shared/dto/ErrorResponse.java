package com.example.skishop.shared.dto;

import java.net.URI;
import java.time.OffsetDateTime;

/**
 * RFC 7807 Problem Details 互換のエラーレスポンス DTO。
 *
 * @param type     エラー種別 URI
 * @param title    エラータイトル
 * @param status   HTTP ステータスコード
 * @param detail   エラー詳細メッセージ
 * @param instance リクエストのパス
 * @param timestamp エラー発生時刻
 */
public record ErrorResponse(
        URI type,
        String title,
        int status,
        String detail,
        String instance,
        OffsetDateTime timestamp
) {
}
