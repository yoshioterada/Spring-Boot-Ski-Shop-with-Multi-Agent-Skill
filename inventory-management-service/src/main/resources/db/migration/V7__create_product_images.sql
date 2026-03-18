-- V7: 商品画像テーブルの作成
CREATE TABLE product_images (
    id         BIGSERIAL NOT NULL,
    product_id BIGINT NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    alt_text   VARCHAR(200),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
        -- 商品削除時に画像も連鎖削除（業務要件: 商品と画像は不可分）
);

CREATE INDEX idx_product_images_product_id ON product_images (product_id);
CREATE INDEX idx_product_images_primary
    ON product_images (product_id) WHERE is_primary = TRUE;
