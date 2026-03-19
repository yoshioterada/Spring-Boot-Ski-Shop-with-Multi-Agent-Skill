-- docker/initdb/01_create_databases.sql
-- ローカル開発用: 単一 PostgreSQL インスタンスに全サービスの DB を作成
-- 本番環境ではサービス毎に独立した Azure PostgreSQL Flexible Server を使用
--
-- 注意: このスクリプトは PostgreSQL データディレクトリが空の場合のみ実行される。
-- DB を作り直したい場合は ./scripts/dev.sh db-reset を実行する。

CREATE DATABASE skishop_auth;
CREATE DATABASE skishop_users;
CREATE DATABASE skishop_sales;
CREATE DATABASE skishop_payment;
CREATE DATABASE point_db;
CREATE DATABASE coupon_db;
CREATE DATABASE skishop_ai;
CREATE DATABASE skishop_mailsend;
