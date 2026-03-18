-- V1: カテゴリテーブルの作成
CREATE TABLE categories (
    id          BIGSERIAL NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    parent_id   BIGINT,
    level       INTEGER NOT NULL DEFAULT 0,
    path        VARCHAR(500) NOT NULL DEFAULT '/',
    image_url   VARCHAR(500),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT uq_categories_name_parent
        UNIQUE (name, parent_id),
    CONSTRAINT ck_categories_level CHECK (level >= 0)
);

CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_categories_is_active ON categories (is_active) WHERE is_active = TRUE;
