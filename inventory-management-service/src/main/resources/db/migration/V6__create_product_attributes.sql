-- V6: 商品属性テーブルの作成
CREATE TABLE product_attributes (
    id              BIGSERIAL NOT NULL,
    product_id      BIGINT NOT NULL,
    attribute_name  VARCHAR(100) NOT NULL,
    attribute_value VARCHAR(500) NOT NULL,
    is_filterable   BOOLEAN NOT NULL DEFAULT FALSE,
    is_sortable     BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_product_attributes PRIMARY KEY (id),
    CONSTRAINT fk_product_attributes_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
        -- 商品削除時に属性も連鎖削除（業務要件: 商品と属性は不可分）
);

CREATE INDEX idx_product_attributes_product_id ON product_attributes (product_id);
CREATE INDEX idx_product_attributes_filterable
    ON product_attributes (attribute_name, attribute_value) WHERE is_filterable = TRUE;
