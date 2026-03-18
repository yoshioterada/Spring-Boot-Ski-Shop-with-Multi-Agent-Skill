-- V4: サプライヤーテーブルの作成
CREATE TABLE suppliers (
    id             BIGSERIAL NOT NULL,
    name           VARCHAR(200) NOT NULL,
    contact_person VARCHAR(100),
    email          VARCHAR(255),
    phone          VARCHAR(50),
    address        VARCHAR(500),
    rating         DECIMAL(3, 2),

    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT ck_suppliers_rating CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);
