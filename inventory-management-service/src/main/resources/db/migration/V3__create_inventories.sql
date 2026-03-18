-- V3: 在庫テーブルの作成
-- 楽観的ロックのために version カラムを含む
CREATE TABLE inventories (
    id                 BIGSERIAL NOT NULL,
    product_id         BIGINT NOT NULL,
    stock_quantity     INTEGER NOT NULL DEFAULT 0,
    reserved_quantity  INTEGER NOT NULL DEFAULT 0,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    warehouse_id       VARCHAR(50) NOT NULL,
    reorder_level      INTEGER NOT NULL DEFAULT 10,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version            BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_inventories PRIMARY KEY (id),
    CONSTRAINT fk_inventories_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
        -- 商品削除時に在庫も連鎖削除（業務要件: 商品と在庫は不可分）
    CONSTRAINT uq_inventories_product_warehouse
        UNIQUE (product_id, warehouse_id),
    CONSTRAINT ck_inventories_stock CHECK (stock_quantity >= 0),
    CONSTRAINT ck_inventories_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventories_available CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventories_reorder CHECK (reorder_level >= 0)
);

CREATE INDEX idx_inventories_product_id ON inventories (product_id);
CREATE INDEX idx_inventories_warehouse_id ON inventories (warehouse_id);
CREATE INDEX idx_inventories_low_stock ON inventories (available_quantity, reorder_level)
    WHERE available_quantity <= reorder_level;
