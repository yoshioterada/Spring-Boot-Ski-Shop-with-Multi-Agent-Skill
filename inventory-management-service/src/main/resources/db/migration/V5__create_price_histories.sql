-- V5: 価格履歴テーブルの作成
CREATE TABLE price_histories (
    id             BIGSERIAL NOT NULL,
    product_id     BIGINT NOT NULL,
    price          DECIMAL(12, 2) NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to   TIMESTAMP WITH TIME ZONE,
    promotion_id   BIGINT,

    CONSTRAINT pk_price_histories PRIMARY KEY (id),
    CONSTRAINT fk_price_histories_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
        -- 商品削除時に価格履歴も連鎖削除（業務要件: 商品データの完全削除）
    CONSTRAINT ck_price_histories_price CHECK (price >= 0),
    CONSTRAINT ck_price_histories_dates
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_price_histories_product_id ON price_histories (product_id);
CREATE INDEX idx_price_histories_effective_from ON price_histories (effective_from DESC);
