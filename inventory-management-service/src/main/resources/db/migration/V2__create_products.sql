-- V2: 商品テーブルの作成
CREATE TABLE products (
    id          BIGSERIAL NOT NULL,
    sku         VARCHAR(100) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    brand       VARCHAR(100),
    category_id BIGINT,
    price       DECIMAL(12, 2) NOT NULL,
    cost        DECIMAL(12, 2),
    weight      DECIMAL(8, 3),
    dimensions  VARCHAR(100),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT ck_products_price CHECK (price >= 0),
    CONSTRAINT ck_products_cost CHECK (cost IS NULL OR cost >= 0)
);

CREATE INDEX idx_products_sku ON products (sku);
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_is_active ON products (is_active) WHERE is_active = TRUE;
CREATE INDEX idx_products_brand ON products (brand);
CREATE INDEX idx_products_price ON products (price);
