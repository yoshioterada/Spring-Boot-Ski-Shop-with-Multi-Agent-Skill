# Ski Shop E-commerce Platform Design Specification

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Design](#architecture-design)
3. [Microservices Architecture](#microservices-architecture)
4. [Technology Stack](#technology-stack)
5. [Data Models](#data-models)
6. [API Design](#api-design)
7. [Authentication & Authorization](#authentication--authorization)
8. [Infrastructure Design](#infrastructure-design)
9. [Non-Functional Requirements](#non-functional-requirements)
10. [Development & Operations Process](#development--operations-process)
11. [Migration Strategy](#migration-strategy)
12. [Risk Management](#risk-management)

## System Overview

### Project Purpose

This project aims to build an e-commerce platform specializing in ski equipment using a microservices architecture. Moving away from traditional monolithic approaches, we will create a system with superior scalability, flexibility, and resilience. The system will feature flexible scaling capabilities to handle highly seasonal demand and provide an integrated online/offline shopping experience.

### Business Requirements

- Online sales of ski equipment (skis, boots, apparel, accessories, etc.)
- Flexible inventory management and pricing to handle seasonal fluctuations
- AI-powered personalized product recommendations and customized shopping experiences
- Support for multiple authentication methods and payment options using OAuth 2.0/OpenID Connect
- Customer loyalty enhancement and retention strategies through point systems and coupon features
- Multi-language support (Japanese and English) with global expansion capabilities
- Multi-device support including mobile devices with responsive design
- Real-time inventory checking and shipping tracking
- Pre-season reservations and limited product sales campaign support
- AI-powered chatbot customer support

### Target User Groups

- Skiers and snowboarders from beginners to advanced levels
- Ski instructors and professional skiers
- Ski resort personnel and ski schools
- Corporate customers such as ski teams and schools
- Ski equipment manufacturers and distributors
- Seasonal buyers and year-round ski enthusiasts

### Core Features

- Comprehensive product catalog with advanced search and filtering capabilities
- Personalized product recommendations
- Seamless checkout process with multiple payment options
- Member registration and profile management
- Order history and order tracking
- Point earning and redemption, coupon application
- Real-time inventory checking and stock notifications
- Product reviews and ratings
- Mobile app and website integration
- AI chatbot customer support

## Architecture Design

### Overall Architecture

This system adopts a microservices architecture with the following characteristics:

- Loose coupling between services and independent deployment cycles
- Unified access point through API gateway
- Event-driven architecture for asynchronous communication
- Independent data stores for each service (polyglot persistence)
- Flexible operations through containerization and orchestration
- Application of cloud-native design principles

### Architecture Diagram

```text
+----------------------------------------------------------------------------------------+
|                                    Client Layer                                        |
|  +----------------+  +------------------+  +-----------------+  +-------------------+  |
|  | Web Browser    |  | Mobile App       |  | Store Terminal  |  | Partner System    |  |
|  +----------------+  +------------------+  +-----------------+  +-------------------+  |
+----------------------------------------------------------------------------------------+
                                        |
                                        | HTTPS
                                        v
+----------------------------------------------------------------------------------------+
|                                   Access Layer                                         |
|  +------------------------------+            +--------------------------------+        |
|  | API Gateway                  |<---------->| OAuth Authentication Service   |        |
|  | (Spring Cloud Gateway/Azure) |            | (User Auth & Authorization)    |        |
|  +------------------------------+            +--------------------------------+        |
+----------------------------------------------------------------------------------------+
                                        |
                                        | REST/gRPC
                                        v
+----------------------------------------------------------------------------------------+
|                                 Application Layer                                      |
|  +------------------+  +----------------+  +-------------------+  +----------------+   |
|  | Product Website  |  | User Management|  | Sales Management  |  | Inventory      |   |
|  | Service          |  | Service        |  | Service           |  | Management     |   |
|  +------------------+  +----------------+  +-------------------+  +----------------+   |
|                                                                                        |
|  +------------------+  +----------------+  +-------------------+  +----------------+   |
|  | AI Support       |  | Payment & Cart |  | Coupon Management |  | Point          |   |
|  | Service          |  | Service        |  | Service           |  | Management     |   |
|  +------------------+  +----------------+  +-------------------+  +----------------+   |
+----------------------------------------------------------------------------------------+
                                        |
                                        | JMS/Kafka
                                        v
+----------------------------------------------------------------------------------------+
|                                   Data Layer                                           |
|  +-----------------+  +---------------+  +----------------+  +------------------+      |
|  | Product DB      |  | User DB       |  | Order & Sales  |  | Inventory DB     |      |
|  | (PostgreSQL)    |  | (MongoDB)     |  | DB (PostgreSQL)|  | (PostgreSQL)     |      |
|  +-----------------+  +---------------+  +----------------+  +------------------+      |
|                                                                                        |
|  +-----------------+  +---------------+  +----------------+  +------------------+      |
|  | AI Analytics    |  | Payment Data  |  | Coupon DB      |  | Point DB         |      |
|  | Data (MongoDB)  |  | (PostgreSQL)  |  | (Redis)        |  | (Redis)          |      |
|  +-----------------+  +---------------+  +----------------+  +------------------+      |
+----------------------------------------------------------------------------------------+
```

### System Boundaries

- **Internal Systems**: All microservices and data stores shown in the architecture diagram above
  
- **External Systems**:
  - Payment gateways (Stripe, PayPal, etc.)
  - Shipping carrier APIs (Yamato Transport, Sagawa Express, etc.)
  - Social media authentication providers (Google, Facebook, Apple, etc.)
  - Email delivery services (SendGrid, etc.)
  - Analytics platforms (Google Analytics, Azure Application Insights, etc.)

### Architecture Patterns

- **API Gateway Pattern**: All client requests pass through a single entry point
- **Circuit Breaker Pattern**: Prevents failure propagation and provides recovery mechanisms
- **CQRS (Command Query Responsibility Segregation)**: Separation of read and write operations for optimization
- **Saga Pattern**: Distributed transaction management
- **Backend for Frontend (BFF)**: Client-specific API layers
- **Strangler Fig Pattern**: Gradual migration to microservices
- **Event Sourcing Pattern**: Recording state changes and event streams

### Communication Patterns

- **Synchronous Communication**:
  - RESTful API (OpenAPI/Swagger specification)
  - GraphQL (client-optimized queries)
  - gRPC (efficient inter-service communication)

- **Asynchronous Communication**:
  - Event-driven (Apache Kafka)
  - Message queues (RabbitMQ)
  - Webhook callbacks (external system integration)

- **Event Message Examples**:
  - OrderCreated: When an order is created
  - PaymentProcessed: When payment processing is completed
  - InventoryUpdated: When inventory is updated
  - UserRegistered: When a user registers
  - ProductViewed: When a product is viewed (for analytics)

## Microservices Architecture

### 1. API Gateway

**Responsibilities**:

- Routing and load balancing of external requests
- Unified authentication and authorization processing
- Rate limiting and throttling
- Request/response transformation
- API documentation aggregation
- Monitoring and logging

**Technology Stack**:

- Development environment: Spring Cloud Gateway
- Production environment: Azure API Management
- Spring Security
- Spring Cloud Circuit Breaker
- JWT authentication

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "API Gateway"
        direction TB
        API[API Gateway Controller] --> AUTH[Authentication Filter]
        API --> ROUTE[Routing Handler]
        API --> THROTTLE[Rate Limiting Component]
        API --> CIRCUIT[Circuit Breaker]
        API --> TRANSFORM[Request/Response Transform]
        API --> LOG[Logging Component]
        API --> METRIC[Metrics Collection]
        
        ROUTE --> ROUTE_CONFIG[Route Configuration]
        AUTH --> JWT[JWT Verification]
        AUTH --> POLICY[Authorization Policy]
        CIRCUIT --> HEALTH[Health Check]
    end
    
    %% External system connections
    API --> MS1[User Management Service]
    API --> MS2[Inventory Management Service]
    API --> MS3[Sales Management Service]
    API --> MS4[Other Microservices]
    JWT --> OAUTH[Authentication Service]
```

**Sequence Diagram (Authentication and Routing)**:

```mermaid
sequenceDiagram
    participant Client as Client
    participant Gateway as API Gateway
    participant Auth as Authentication Service
    participant Service as Microservice
    
    Client->>Gateway: Send Request
    Gateway->>Gateway: Request Validation
    Gateway->>Gateway: Rate Limit Check
    
    alt Authentication Required Endpoint
        Gateway->>Auth: Token Verification Request
        Auth->>Auth: JWT Verification
        Auth-->>Gateway: Verification Result
        
        alt Authentication Failed
            Gateway-->>Client: 401/403 Error Response
        end
    end
    
    Gateway->>Gateway: Route Decision
    Gateway->>Service: Forward Request
    
    alt Circuit Breaker Open
        Gateway-->>Client: Service Temporarily Unavailable Response
    else Circuit Breaker Closed
        Service-->>Gateway: Service Response
        Gateway->>Gateway: Response Transformation
        Gateway-->>Client: Final Response
    end
    
    Gateway->>Gateway: Record Metrics
    Gateway->>Gateway: Record Logs
```

### 2. User Management Service

**Responsibilities**:

- User registration, authentication, and profile management
- User role and permission management
- Account settings and preference management
- Personalization information management
- User activity tracking

**Core Entities**:

- User
- Role
- Permission
- UserPreference
- UserActivity

**Data Store**:

- PostgreSQL (user data)
- Redis (session information, temporary data)

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "User Management Service"
        direction TB
        API[REST API Controller] --> SERV[User Service]
        SERV --> REPO[User Repository]
        SERV --> ROLE_SERV[Role Service]
        SERV --> PREF_SERV[Preference Service]
        SERV --> ACT_SERV[Activity Service]
        
        ROLE_SERV --> ROLE_REPO[Role Repository]
        PREF_SERV --> PREF_REPO[Preference Repository]
        ACT_SERV --> ACT_REPO[Activity Repository]
        
        REPO --> DB[(PostgreSQL)]
        ROLE_REPO --> DB
        PREF_REPO --> DB
        ACT_REPO --> DB
        
        CACHE[Cache Service] --> REDIS[(Redis)]
        SERV --> CACHE
        
        EVENT[Event Publisher] --> KAFKA[Kafka]
        SERV --> EVENT
        
        VALID[Validation Component]
        SERV --> VALID
    end
    
    %% External system connections
    API --> AUTH[Authentication Service]
    API --> GATEWAY[API Gateway]
```

**Sequence Diagram (User Registration Process)**:

```mermaid
sequenceDiagram
    participant Client as Client
    participant Gateway as API Gateway
    participant UserAPI as User API
    participant UserService as User Service
    participant Validation as Validation
    participant DB as Database
    participant EventBus as Event Bus
    
    Client->>Gateway: User Registration Request
    Gateway->>UserAPI: Forward Request
    UserAPI->>Validation: Input Validation
    
    alt Validation Error
        Validation-->>UserAPI: Validation Error
        UserAPI-->>Gateway: 400 Error Response
        Gateway-->>Client: Error Response
    else Validation Success
        Validation-->>UserAPI: Validation Success
        UserAPI->>UserService: Create User
        UserService->>DB: Save User Information
        DB-->>UserService: Save Success
        
        UserService->>EventBus: Publish User Created Event
        UserService-->>UserAPI: User Creation Success
        
        UserAPI->>UserAPI: Generate JWT Token
        UserAPI-->>Gateway: 201 Created Response
        Gateway-->>Client: Registration Success Response
    end
    
    opt Optional: Email Confirmation
        UserService->>UserService: Send Confirmation Email
        Client->>Gateway: Click Email Confirmation Link
        Gateway->>UserAPI: Confirmation Request
        UserAPI->>UserService: Email Confirmation Process
        UserService->>DB: Update User Status
        DB-->>UserService: Update Success
        UserService-->>UserAPI: Confirmation Success
        UserAPI-->>Gateway: Success Response
        Gateway-->>Client: Confirmation Complete Page
    end
```

### 3. Inventory Management Service

**Responsibilities**:

- Product catalog management
- Inventory status tracking and updates
- Product attributes and category management
- Pricing and discount management
- Receiving and shipping operations
- Product image and media management

**Core Entities**:

- Product
- Category
- Inventory
- Supplier
- PriceHistory
- ProductImage

**Data Store**:

- MongoDB (product catalog, attributes)
- PostgreSQL (inventory information, transactions)
- Blob Storage (product images)

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "Inventory Management Service"
        direction TB
        API[REST API Controller] --> PROD_SERV[Product Service]
        API --> INV_SERV[Inventory Service]
        API --> CAT_SERV[Category Service]
        API --> PRICE_SERV[Pricing Service]
        API --> IMG_SERV[Media Service]
        
        PROD_SERV --> PROD_REPO[Product Repository]
        INV_SERV --> INV_REPO[Inventory Repository]
        CAT_SERV --> CAT_REPO[Category Repository]
        PRICE_SERV --> PRICE_REPO[Price Repository]
        IMG_SERV --> IMG_REPO[Image Repository]
        
        PROD_REPO --> MONGO[(MongoDB)]
        CAT_REPO --> MONGO
        
        INV_REPO --> PSQL[(PostgreSQL)]
        PRICE_REPO --> PSQL
        
        IMG_REPO --> BLOB[(Azure Blob Storage)]
        
        EVENT[Event Publisher] --> KAFKA[Kafka]
        INV_SERV --> EVENT
        PRICE_SERV --> EVENT
        
        CACHE[Cache Service] --> REDIS[(Redis)]
        PROD_SERV --> CACHE
        CAT_SERV --> CACHE
        
        SEARCH[Search Index Service] --> ES[(Elasticsearch)]
        PROD_SERV --> SEARCH
    end
    
    %% External system connections
    API --> GATEWAY[API Gateway]
    EVENT --> SALES[Sales Management Service]
    EVENT --> AI[AI Support Service]
```

**Sequence Diagram (Inventory Update Process)**:

```mermaid
sequenceDiagram
    participant Gateway as API Gateway
    participant InvAPI as Inventory API
    participant InvService as Inventory Service
    participant ProdService as Product Service
    participant DB as Database
    participant Cache as Cache
    participant EventBus as Event Bus
    participant Sales as Sales Management Service
    
    alt Receiving Process
        Gateway->>InvAPI: Send Receiving Information
        InvAPI->>InvService: Receiving Process Request
        InvService->>DB: Check Current Inventory
        DB-->>InvService: Inventory Information
        InvService->>DB: Update Inventory Count
        DB-->>InvService: Update Success
        InvService->>EventBus: Publish Inventory Update Event
        EventBus->>Sales: Inventory Change Notification
    end
    
    alt Inventory Reduction from Sales
        Sales->>EventBus: Order Confirmed Event
        EventBus->>InvService: Inventory Reduction Request
        InvService->>DB: Check Inventory
        
        alt Sufficient Inventory
            InvService->>DB: Reduce Inventory Count
            DB-->>InvService: Update Success
            InvService->>EventBus: Inventory Update Complete Event
            EventBus->>Sales: Inventory Reserved Complete Notification
        else Insufficient Inventory
            InvService->>EventBus: Insufficient Inventory Event
            EventBus->>Sales: Insufficient Inventory Notification
        end
    end
    
    InvAPI->>InvService: Get Inventory Status
    InvService->>Cache: Check Cache
    
    alt Cache Hit
        Cache-->>InvService: Cache Data
    else Cache Miss
        InvService->>DB: Get Inventory Data
        DB-->>InvService: Inventory Data
        InvService->>Cache: Update Cache
    end
    
    InvService-->>InvAPI: Inventory Information
    InvAPI-->>Gateway: Return Response
```

### 4. Sales Management Service

**Responsibilities**:

- Order processing and management
- Sales analysis and reporting
- Return and exchange processing
- Shipping coordination and tracking
- Sales history

**Core Entities**:

- Order
- OrderItem
- Shipment
- Return
- Invoice

**Data Store**:

- PostgreSQL (orders, shipping information)
- Elasticsearch (search, analysis)

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "Sales Management Service"
        direction TB
        API[REST API Controller] --> ORDER_SERV[Order Service]
        API --> SHIP_SERV[Shipment Service]
        API --> RET_SERV[Return Service]
        API --> REP_SERV[Report Service]
        
        ORDER_SERV --> ORDER_REPO[Order Repository]
        SHIP_SERV --> SHIP_REPO[Shipment Repository]
        RET_SERV --> RET_REPO[Return Repository]
        
        ORDER_REPO --> DB[(PostgreSQL)]
        SHIP_REPO --> DB
        RET_REPO --> DB
        
        REP_SERV --> ES[(Elasticsearch)]
        
        EVENT[Event Publisher] --> KAFKA[Kafka]
        ORDER_SERV --> EVENT
        SHIP_SERV --> EVENT
        
        ORDER_SERV --> SAGA[Saga Coordinator]
        
        ANALYSIS[Analytics Engine] --> ES
        REP_SERV --> ANALYSIS
        
        EXPORT[Report Exporter]
        REP_SERV --> EXPORT
    end
    
    %% External system connections
    API --> GATEWAY[API Gateway]
    EVENT --> INV[Inventory Management Service]
    EVENT --> PAY[Payment & Cart Service]
    EVENT --> POINT[Point Management Service]
    SAGA --> INV
    SAGA --> PAY
    SAGA --> POINT
```

**Sequence Diagram (Order Processing Flow)**:

```mermaid
sequenceDiagram
    participant Client as Client
    participant Gateway as API Gateway
    participant SalesAPI as Sales API
    participant OrderService as Order Service
    participant SagaCoord as Saga Coordinator
    participant PaymentService as Payment Service
    participant InventoryService as Inventory Service
    participant ShipmentService as Shipment Service
    participant PointService as Point Service
    participant DB as Database
    
    Client->>Gateway: Order Confirmation Request
    Gateway->>SalesAPI: Forward Request
    SalesAPI->>OrderService: Order Processing Request
    OrderService->>SagaCoord: Start Transaction
    
    %% Inventory Check Step
    SagaCoord->>InventoryService: Inventory Check & Reserve
    
    alt Insufficient Inventory
        InventoryService-->>SagaCoord: Insufficient Inventory Error
        SagaCoord-->>OrderService: Transaction Failed
        OrderService-->>SalesAPI: Processing Failed
        SalesAPI-->>Gateway: Error Response
        Gateway-->>Client: Insufficient Inventory Error
    else Inventory Reserved Successfully
        InventoryService-->>SagaCoord: Inventory Reserved Complete
        
        %% Payment Processing Step
        SagaCoord->>PaymentService: Payment Processing
        
        alt Payment Failed
            PaymentService-->>SagaCoord: Payment Failed
            SagaCoord->>InventoryService: Cancel Inventory Reservation
            InventoryService-->>SagaCoord: Inventory Released Complete
            SagaCoord-->>OrderService: Transaction Failed
            OrderService-->>SalesAPI: Processing Failed
            SalesAPI-->>Gateway: Error Response
            Gateway-->>Client: Payment Failed Error
        else Payment Successful
            PaymentService-->>SagaCoord: Payment Successful
            
            %% Order Registration
            SagaCoord->>OrderService: Confirm Order
            OrderService->>DB: Save Order Information
            DB-->>OrderService: Save Successful
            
            %% Shipping Arrangement
            SagaCoord->>ShipmentService: Arrange Shipping
            ShipmentService->>DB: Register Shipping Information
            DB-->>ShipmentService: Registration Successful
            ShipmentService-->>SagaCoord: Shipping Arrangement Complete
            
            %% Point Award
            SagaCoord->>PointService: Award Points
            PointService-->>SagaCoord: Point Processing Complete
            
            %% Transaction Complete
            SagaCoord-->>OrderService: Transaction Successful
            OrderService-->>SalesAPI: Processing Successful
            SalesAPI-->>Gateway: Success Response
            Gateway-->>Client: Order Completion Confirmation
        end
    end
```

### 5. AI-Enabled Support Service

**Responsibilities**:

- Personalized product recommendations
- Search optimization and autocomplete
- Chatbot customer support
- Demand forecasting
- User behavior analytics

**Technology Stack**:

- Azure AI Services
- Azure OpenAI Service
- Spring AI
- Vector Database (Pinecone, Qdrant, etc.)

**Data Flow**:

- User behavior data collection
- Product recommendation model training and deployment
- Real-time recommendations and search enhancement

**コンポーネント構成図**:

```mermaid
graph TB
    subgraph "AI対応サポート機能サービス"
        direction TB
        API[REST/GraphQL API] --> REC_SERV[Recommendation Service]
        API --> SEARCH_SERV[Search Service]
        API --> CHAT_SERV[Chatbot Service]
        API --> FORECAST_SERV[Forecast Service]
        API --> ANALYTICS_SERV[Analytics Service]
        
        REC_SERV --> VECTOR_DB[(Vector Database)]
        SEARCH_SERV --> VECTOR_DB
        SEARCH_SERV --> ES[(Elasticsearch)]
        
        CHAT_SERV --> OPENAI[Azure OpenAI]
        CHAT_SERV --> KNOWLEDGE[Knowledge Base]
        
        FORECAST_SERV --> ML_MODELS[ML Models]
        ANALYTICS_SERV --> DATA_LAKE[(Data Lake)]
        
        ETL[ETL Pipeline] --> DATA_LAKE
        ML_PIPELINE[ML Training Pipeline] --> ML_MODELS
        ETL --> ML_PIPELINE
        
        REC_ENGINE[Recommendation Engine] --> ML_MODELS
        REC_ENGINE --> VECTOR_DB
        REC_SERV --> REC_ENGINE
        
        EVENT_CONSUMER[Event Consumer] --> KAFKA[Kafka]
        EVENT_CONSUMER --> ETL
    end
    
    %% 外部システムとの接続
    API --> GATEWAY[APIゲートウェイ]
    EVENT_CONSUMER --> USER[ユーザー管理サービス]
    EVENT_CONSUMER --> INVENTORY[在庫管理サービス]
    EVENT_CONSUMER --> SALES[販売管理サービス]
```

**シーケンス図（商品レコメンデーション）**:

```mermaid
sequenceDiagram
    participant Client as クライアント
    participant Gateway as APIゲートウェイ
    participant AIAPI as AI API
    participant RecService as レコメンデーションサービス
    participant UserService as ユーザー管理サービス
    participant VectorDB as ベクトルDB
    participant MLModel as 機械学習モデル
    
    Client->>Gateway: 商品ページ閲覧
    Gateway->>AIAPI: レコメンデーション要求
    
    %% ユーザー情報取得
    AIAPI->>UserService: ユーザープロフィール取得
    UserService-->>AIAPI: ユーザー情報
    
    %% 商品ベクトル検索
    AIAPI->>RecService: ユーザー向けレコメンデーション
    RecService->>VectorDB: 現在の商品ベクトル取得
    VectorDB-->>RecService: 商品ベクトル
    
    %% パーソナライズドレコメンデーション
    RecService->>MLModel: レコメンデーション計算
    MLModel->>MLModel: ユーザー嗜好との類似性計算
    MLModel-->>RecService: レコメンデーション結果
    
    RecService->>RecService: コンテキスト考慮フィルタリング
    RecService-->>AIAPI: レコメンド商品リスト
    AIAPI-->>Gateway: レコメンド結果レスポンス
    Gateway-->>Client: パーソナライズド商品表示
    
    %% 行動追跡（非同期）
    Client->>Gateway: 商品インタラクション
    Gateway->>AIAPI: ユーザーアクション記録
    AIAPI->>RecService: フィードバック登録
    RecService->>VectorDB: ユーザー行動プロファイル更新
    
    %% 定期バッチ処理（別シーケンス）
    Note over MLModel: 定期的なモデル再トレーニング（夜間バッチ）
```

### 6. 支払い・カート処理サービス

**責務**:

- ショッピングカート管理
- 支払い処理とゲートウェイ連携
- 価格計算と税金計算
- 注文確認と領収書生成
- 決済セキュリティ（PCI DSS対応）

**主要エンティティ**:

- Cart
- CartItem
- Payment
- PaymentMethod
- Transaction

**データストア**:

- Redis（カート情報）
- PostgreSQL（支払い記録、トランザクション）

**コンポーネント構成図**:

```mermaid
graph TB
    subgraph "支払い・カート処理サービス"
        direction TB
        API[REST API Controller] --> CART_SERV[Cart Service]
        API --> PAY_SERV[Payment Service]
        API --> PRICE_SERV[Pricing Service]
        API --> DOC_SERV[Document Service]
        
        CART_SERV --> CART_REPO[Cart Repository]
        PAY_SERV --> PAY_REPO[Payment Repository]
        PRICE_SERV --> PRICE_REPO[Pricing Repository]
        
        CART_REPO --> REDIS[(Redis)]
        PAY_REPO --> DB[(PostgreSQL)]
        PRICE_REPO --> DB
        
        PAY_SERV --> PAY_GATEWAY[Payment Gateway Adapter]
        PAY_GATEWAY --> EXTERNAL[外部決済サービス]
        
        EVENT[Event Publisher] --> KAFKA[Kafka]
        PAY_SERV --> EVENT
        CART_SERV --> EVENT
        
        DOC_SERV --> TEMPLATE[Template Engine]
        DOC_SERV --> PDF[PDF Generator]
        
        SECURITY[Security Component]
        PAY_SERV --> SECURITY
        PAY_GATEWAY --> SECURITY
    end
    
    %% 外部システムとの接続
    API --> GATEWAY[APIゲートウェイ]
    EVENT --> SALES[販売管理サービス]
    EVENT --> INVENTORY[在庫管理サービス]
    EVENT --> COUPON[クーポンサービス]
    EVENT --> POINT[ポイントサービス]
```

**シーケンス図（カート追加と支払いプロセス）**:

```mermaid
sequenceDiagram
    participant Client as クライアント
    participant Gateway as APIゲートウェイ
    participant CartAPI as カートAPI
    participant CartService as カートサービス
    participant PriceService as 価格計算サービス
    participant PayService as 支払いサービス
    participant PayGateway as 決済ゲートウェイ
    participant Redis as Redisキャッシュ
    participant DB as データベース
    participant EventBus as イベントバス
    
    %% カートへの商品追加フロー
    Client->>Gateway: 商品をカートに追加
    Gateway->>CartAPI: リクエスト転送
    CartAPI->>CartService: カート更新処理
    CartService->>Redis: 現在のカート取得
    Redis-->>CartService: カート情報
    CartService->>Redis: カート更新
    Redis-->>CartService: 更新成功
    CartService->>PriceService: 小計計算要求
    PriceService->>PriceService: 税金・送料・割引計算
    PriceService-->>CartService: 計算結果
    CartService-->>CartAPI: 更新完了レスポンス
    CartAPI-->>Gateway: カート情報
    Gateway-->>Client: カート更新完了
    
    %% 支払い処理フロー
    Client->>Gateway: 支払い処理開始
    Gateway->>CartAPI: チェックアウト要求
    CartAPI->>PayService: 支払い処理開始
    
    PayService->>CartService: カート情報取得
    CartService->>Redis: カート読み取り
    Redis-->>CartService: カート情報
    CartService-->>PayService: カート情報
    
    PayService->>PayService: 支払い情報検証
    PayService->>PayGateway: 支払い処理要求
    PayGateway->>PayGateway: 決済情報暗号化
    PayGateway->>ExternalPayment: 外部決済サービス呼び出し
    
    alt 支払い成功
        ExternalPayment-->>PayGateway: 支払い成功
        PayGateway-->>PayService: 支払い完了通知
        PayService->>DB: トランザクション記録
        DB-->>PayService: 記録完了
        
        PayService->>EventBus: 支払い完了イベント発行
        EventBus->>SalesService: 注文処理通知
        
        PayService-->>CartAPI: 支払い完了レスポンス
        CartAPI-->>Gateway: 支払い成功レスポンス
        Gateway-->>Client: 支払い成功表示
        
        %% カートクリア（非同期）
        PayService->>CartService: カートクリア要求
        CartService->>Redis: カート削除
    else 支払い失敗
        ExternalPayment-->>PayGateway: 支払い失敗
        PayGateway-->>PayService: エラー通知
        PayService-->>CartAPI: 支払い失敗レスポンス
        CartAPI-->>Gateway: エラーレスポンス
        Gateway-->>Client: 支払い失敗メッセージ
    end
```

### 7. Authentication Service (OAuth)

**Responsibilities**:

- OAuth 2.0/OpenID Connect flow processing
- JWT token generation and validation
- Social login integration
- Multi-Factor Authentication (MFA)
- Session management

**Technology Stack**:

- Spring Security OAuth2
- Keycloak
- JWT

**Security Features**:

- Token-based authentication
- Access token and refresh token management
- Scope-based authorization control
- Token revocation management

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "Authentication Service (OAuth)"
        direction TB
        API[OAuth API Controller] --> AUTH_SERVER[Authorization Server]
        API --> TOKEN_SERV[Token Service]
        API --> USER_AUTH[User Authentication]
        API --> SOCIAL[Social Login Service]
        API --> MFA[MFA Service]
        
        AUTH_SERVER --> CLIENT_REPO[Client Repository]
        TOKEN_SERV --> TOKEN_REPO[Token Repository]
        USER_AUTH --> CRED_SERV[Credential Service]
        
        TOKEN_SERV --> JWT_GEN[JWT Generator]
        TOKEN_SERV --> TOKEN_VALID[Token Validator]
        
        SOCIAL --> SOCIAL_PROVIDERS[Social Identity Providers]
        MFA --> MFA_PROVIDERS[MFA Providers]
        
        CACHE[Token Cache] --> REDIS[(Redis)]
        TOKEN_SERV --> CACHE
        
        CLIENT_REPO --> DB[(PostgreSQL)]
        TOKEN_REPO --> DB
        
        SECURITY[Security Components]
        AUTH_SERVER --> SECURITY
        TOKEN_SERV --> SECURITY
        
        EVENT[Event Publisher] --> KAFKA[Kafka]
        USER_AUTH --> EVENT
    end
    
    %% External system connections
    API --> GATEWAY[API Gateway]
    USER_AUTH --> USER_SERVICE[User Management Service]
    SOCIAL_PROVIDERS --> EXTERNAL[External ID Providers]
```

**Sequence Diagram (OAuth Authentication Flow)**:

```mermaid
sequenceDiagram
    participant Client as Client
    participant Frontend as Frontend
    participant Gateway as API Gateway
    participant AuthService as Authentication Service
    participant UserService as User Management Service
    participant SocialProvider as External ID Provider
    participant TokenService as Token Service
    participant Cache as Token Cache
    
    %% Authorization code flow
    Client->>Frontend: Login request
    Frontend->>Gateway: Authentication start request
    Gateway->>AuthService: /oauth/authorize
    AuthService->>AuthService: Client validation
    
    alt Social login
        AuthService->>SocialProvider: Redirect
        Client->>SocialProvider: Authentication
        SocialProvider-->>Client: Redirect with authorization code
        Client->>Frontend: Authorization code
        Frontend->>Gateway: Send authorization code
        Gateway->>AuthService: /oauth/token
        AuthService->>SocialProvider: Token validation
        SocialProvider-->>AuthService: User information
        
        alt First time login
            AuthService->>UserService: Create user
            UserService-->>AuthService: User information
        else Existing user
            AuthService->>UserService: Get user
            UserService-->>AuthService: User information
        end
    else Password authentication
        AuthService-->>Client: Login form
        Client->>AuthService: Send credentials
        AuthService->>UserService: Validate credentials
        UserService-->>AuthService: Authentication result
        
        alt MFA required
            AuthService-->>Client: MFA challenge
            Client->>AuthService: MFA response
            AuthService->>AuthService: MFA validation
        end
    end
    
    %% Token generation
    AuthService->>TokenService: Token generation request
    TokenService->>TokenService: Generate JWT token
    TokenService->>Cache: Save refresh token
    Cache-->>TokenService: Save successful
    TokenService-->>AuthService: Token pair
    
    AuthService-->>Gateway: Access & refresh tokens
    Gateway-->>Frontend: Send tokens
    Frontend->>Frontend: Store tokens
    Frontend-->>Client: Login successful
    
    %% Token refresh (separate sequence)
    Note over Client, Cache: Token refresh flow
    Frontend->>Gateway: Send refresh token
    Gateway->>AuthService: /oauth/token (grant_type=refresh_token)
    AuthService->>TokenService: Token validation
    TokenService->>Cache: Validate refresh token
    Cache-->>TokenService: Validation result
    TokenService->>TokenService: Generate new JWT token
    TokenService-->>AuthService: New token pair
    AuthService-->>Gateway: New access & refresh tokens
    Gateway-->>Frontend: New tokens
```

### 8. Product Sales Website (Frontend)

**Responsibilities**:

- Responsive web interface provision
- User experience optimization
- Client-side performance optimization
- Multi-language support
- SEO optimization

**Technology Stack**:

- Next.js (React)
- TypeScript
- TailwindCSS
- Redux/Zustand
- GraphQL (Apollo Client)

**Accessibility**:

- WCAG 2.1 AA compliance
- Keyboard navigation
- Screen reader support

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "Product Sales Website (Frontend)"
        direction TB
        PAGES[Next.js Pages] --> LAYOUTS[Layout Components]
        PAGES --> COMP[UI Components]
        PAGES --> HOOKS[Custom Hooks]
        
        LAYOUTS --> HEADER[Header Component]
        LAYOUTS --> FOOTER[Footer Component]
        LAYOUTS --> NAV[Navigation Component]
        
        COMP --> PRODUCT[Product Components]
        COMP --> CART[Cart Components]
        COMP --> CHECKOUT[Checkout Components]
        COMP --> USER[User Components]
        COMP --> SEARCH[Search Components]
        
        STATE[State Management] --> REDUX[Redux Store]
        STATE --> CONTEXT[React Context]
        
        API[API Layer] --> REST[REST Client]
        API --> GRAPHQL[GraphQL Client]
        
        SERVICES[Services] --> AUTH[Auth Service]
        SERVICES --> CART_SERV[Cart Service]
        SERVICES --> PRODUCT_SERV[Product Service]
        SERVICES --> USER_SERV[User Service]
        
        UTILS[Utilities] --> I18N[Internationalization]
        UTILS --> VALIDATORS[Form Validators]
        UTILS --> FORMATTER[Data Formatters]
        
        HOOKS --> API
        COMP --> STATE
        COMP --> SERVICES
        SERVICES --> API
    end
    
    %% External system connections
    API --> GATEWAY[API Gateway]
    AUTH --> AUTH_SERVICE[Authentication Service]
```

**Sequence Diagram (Product Browsing and Purchase Flow)**:

```mermaid
sequenceDiagram
    participant User as User
    participant Browser as Browser
    participant FrontEnd as Web Frontend
    participant ApiGateway as API Gateway
    participant AuthService as Authentication Service
    participant ProductService as Inventory Management Service
    participant CartService as Cart Service
    participant AIService as AI Support Service
    participant PaymentService as Payment Service
    
    %% Initial page display
    User->>Browser: Site access
    Browser->>FrontEnd: Page request
    
    alt Server-side rendering (SSR)
        FrontEnd->>ApiGateway: Data fetch
        ApiGateway->>ProductService: Product data request
        ProductService-->>ApiGateway: Product data
        ApiGateway-->>FrontEnd: Data response
        FrontEnd->>FrontEnd: Page rendering
        FrontEnd-->>Browser: HTML response
    else Static site generation (SSG)
        FrontEnd-->>Browser: Pre-generated HTML
        Browser->>FrontEnd: Hydration
    end
    
    Browser-->>User: Page display
    
    %% User authentication
    User->>Browser: Click login
    Browser->>FrontEnd: Display login screen
    FrontEnd-->>Browser: Login form
    User->>Browser: Enter credentials
    Browser->>FrontEnd: Login request
    FrontEnd->>ApiGateway: Authentication request
    ApiGateway->>AuthService: Authentication processing
    AuthService-->>ApiGateway: JWT token
    ApiGateway-->>FrontEnd: Authentication success response
    FrontEnd->>FrontEnd: Store token
    FrontEnd-->>Browser: Display login success
    Browser-->>User: Login complete
    
    %% Product browsing
    User->>Browser: Product search/category selection
    Browser->>FrontEnd: Product list request
    FrontEnd->>ApiGateway: Product search API call
    ApiGateway->>ProductService: Product search
    ProductService-->>ApiGateway: Product list
    ApiGateway->>AIService: Personalized recommendations
    AIService-->>ApiGateway: Recommended products
    ApiGateway-->>FrontEnd: Search results + recommendations
    FrontEnd-->>Browser: Product list display
    Browser-->>User: Product display
    
    %% Cart operations
    User->>Browser: Add product to cart
    Browser->>FrontEnd: Cart add action
    FrontEnd->>ApiGateway: Cart update API call
    ApiGateway->>CartService: Cart update
    CartService-->>ApiGateway: Update successful
    ApiGateway-->>FrontEnd: Cart information
    FrontEnd-->>Browser: Update cart display
    Browser-->>User: Cart addition complete notification
    
    %% Payment processing
    User->>Browser: Click checkout button
    Browser->>FrontEnd: Checkout screen request
    FrontEnd->>ApiGateway: Get cart information
    ApiGateway->>CartService: Get cart
    CartService-->>ApiGateway: Cart details
    ApiGateway-->>FrontEnd: Cart information
    FrontEnd-->>Browser: Order confirmation screen
    
    User->>Browser: Enter payment information & confirm
    Browser->>FrontEnd: Payment processing request
    FrontEnd->>ApiGateway: Payment processing API call
    ApiGateway->>PaymentService: Payment processing
    
    alt Payment successful
        PaymentService-->>ApiGateway: Payment successful
        ApiGateway-->>FrontEnd: Payment success response
        FrontEnd-->>Browser: Order completion screen
        Browser-->>User: Order confirmation display
    else Payment failed
        PaymentService-->>ApiGateway: Payment failed
        ApiGateway-->>FrontEnd: Error response
        FrontEnd-->>Browser: Error display
        Browser-->>User: Payment failure notification
    end
```

### 9. Coupon Service

**Responsibilities**:

- Coupon creation and management
- Coupon distribution rule configuration
- Coupon application and validation
- Campaign management
- Coupon usage tracking

**Main Entities**:

- Coupon
- CouponType
- CouponUsage
- Campaign
- Promotion

**Data Store**:

- PostgreSQL (coupon data)
- Redis (active coupons, cache)

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "Coupon Service"
        direction TB
        API[REST API Controller] --> COUPON_SERV[Coupon Service]
        API --> CAMPAIGN_SERV[Campaign Service]
        API --> VALID_SERV[Validation Service]
        API --> DIST_SERV[Distribution Service]
        
        COUPON_SERV --> COUPON_REPO[Coupon Repository]
        CAMPAIGN_SERV --> CAMPAIGN_REPO[Campaign Repository]
        VALID_SERV --> USAGE_REPO[Usage Repository]
        DIST_SERV --> DIST_REPO[Distribution Repository]
        
        COUPON_REPO --> DB[(PostgreSQL)]
        CAMPAIGN_REPO --> DB
        USAGE_REPO --> DB
        DIST_REPO --> DB
        
        CACHE[Cache Service] --> REDIS[(Redis)]
        COUPON_SERV --> CACHE
        VALID_SERV --> CACHE
        
        RULE_ENGINE[Rule Engine]
        VALID_SERV --> RULE_ENGINE
        DIST_SERV --> RULE_ENGINE
        
        EVENT[Event Publisher] --> KAFKA[Kafka]
        COUPON_SERV --> EVENT
        VALID_SERV --> EVENT
        
        GENERATOR[Coupon Code Generator]
        COUPON_SERV --> GENERATOR
    end
    
    %% External system connections
    API --> GATEWAY[API Gateway]
    EVENT --> SALES[Sales Management Service]
    EVENT --> USER[User Management Service]
    EVENT --> CART[Cart Processing Service]
```

**Sequence Diagram (Coupon Application Process)**:

```mermaid
sequenceDiagram
    participant Client as Client
    participant Gateway as API Gateway
    participant CartService as Cart Service
    participant CouponAPI as Coupon API
    participant ValidService as Validation Service
    participant RuleEngine as Rule Engine
    participant Cache as Redis Cache
    participant DB as Database
    participant EventBus as Event Bus
    
    %% Coupon code entry
    Client->>Gateway: Send coupon code
    Gateway->>CartService: Coupon application request
    CartService->>CouponAPI: Coupon validation request
    
    %% Coupon validation
    CouponAPI->>ValidService: Coupon validation
    ValidService->>Cache: Get coupon information
    
    alt Cache hit
        Cache-->>ValidService: Coupon information
    else Cache miss
        ValidService->>DB: Query coupon information
        DB-->>ValidService: Coupon information
        ValidService->>Cache: Cache coupon information
    end
    
    %% Rule validation
    ValidService->>RuleEngine: Check application conditions
    RuleEngine->>RuleEngine: Rule evaluation
    
    alt Invalid coupon
        RuleEngine-->>ValidService: Invalid (expired/used etc.)
        ValidService-->>CouponAPI: Validation failed
        CouponAPI-->>CartService: Invalid coupon response
        CartService-->>Gateway: Error response
        Gateway-->>Client: Invalid coupon message
    else Valid but conditions not met
        RuleEngine-->>ValidService: Conditions not met
        ValidService-->>CouponAPI: Condition error
        CouponAPI-->>CartService: Condition not met response
        CartService-->>Gateway: Condition error response
        Gateway-->>Client: Condition not met message
    else Valid coupon
        RuleEngine-->>ValidService: Validation successful
        ValidService-->>CouponAPI: Coupon details
        CouponAPI-->>CartService: Discount information
        
        %% Apply to cart
        CartService->>CartService: Calculate discount
        CartService-->>Gateway: Updated cart information
        Gateway-->>Client: Display applied discount
        
        %% Reserve usage (confirm on order completion)
        ValidService->>Cache: Reserve coupon usage
    end
    
    %% Confirm coupon usage on order completion
    Note over Client, EventBus: On order completion
    CartService->>CouponAPI: Confirm coupon usage
    CouponAPI->>ValidService: Record usage
    ValidService->>DB: Save usage information
    DB-->>ValidService: Save successful
    ValidService->>EventBus: Coupon usage event
    EventBus->>Analytics: Update usage statistics
```

### 10. Point Management Service

**Responsibilities**:

- Point granting, consumption, and calculation
- Point rule management
- Point history tracking
- Point expiry management
- Point campaigns

**Main Entities**:

- PointAccount
- PointTransaction
- PointRule
- PointExpiry
- PointCampaign

**Data Store**:

- PostgreSQL (point data, transactions)
- Redis (real-time point information)

**Component Architecture Diagram**:

```mermaid
graph TB
    subgraph "Point Management Service"
        direction TB
        API[REST API Controller] --> POINT_SERV[Point Service]
        API --> RULE_SERV[Rule Service]
        API --> CAMPAIGN_SERV[Campaign Service]
        API --> HISTORY_SERV[History Service]
        
        POINT_SERV --> ACCOUNT_REPO[Account Repository]
        POINT_SERV --> TRANS_REPO[Transaction Repository]
        RULE_SERV --> RULE_REPO[Rule Repository]
        CAMPAIGN_SERV --> CAMPAIGN_REPO[Campaign Repository]
        HISTORY_SERV --> HISTORY_REPO[History Repository]
        
        ACCOUNT_REPO --> DB[(PostgreSQL)]
        TRANS_REPO --> DB
        RULE_REPO --> DB
        CAMPAIGN_REPO --> DB
        HISTORY_REPO --> DB
        
        CACHE[Cache Service] --> REDIS[(Redis)]
        POINT_SERV --> CACHE
        
        CALC_ENGINE[Calculation Engine]
        POINT_SERV --> CALC_ENGINE
        RULE_SERV --> CALC_ENGINE
        
        EXPIRY[Expiry Manager]
        POINT_SERV --> EXPIRY
        EXPIRY --> BATCH[Batch Processor]
        
        EVENT[Event Publisher] --> KAFKA[Kafka]
        POINT_SERV --> EVENT
        CAMPAIGN_SERV --> EVENT
    end
    
    %% External system connections
    API --> GATEWAY[API Gateway]
    EVENT --> SALES[Sales Management Service]
    EVENT --> USER[User Management Service]
    EVENT --> NOTIFICATION[Notification Service]
```

**Sequence Diagram (Point Grant Process)**:

```mermaid
sequenceDiagram
    participant Sales as Sales Management Service
    participant EventBus as Event Bus
    participant PointAPI as Point API
    participant PointService as Point Service
    participant RuleService as Rule Service
    participant CalcEngine as Calculation Engine
    participant DB as Database
    participant Cache as Redis Cache
    participant User as User Management Service
    participant Notification as Notification Service
    
    %% Point grant flow starting from order completion event
    Sales->>EventBus: Order completion event
    EventBus->>PointAPI: Point grant request
    
    %% Get user information
    PointAPI->>PointService: Point calculation request
    PointService->>User: Get user information
    User-->>PointService: User information (membership rank etc.)
    
    %% Point calculation
    PointService->>RuleService: Get applicable rules
    RuleService->>DB: Get rule information
    DB-->>RuleService: Point rules
    
    alt User benefits or campaigns
        RuleService->>DB: Get campaign information
        DB-->>RuleService: Active campaigns
    end
    
    RuleService-->>PointService: Rule information
    PointService->>CalcEngine: Calculate points
    CalcEngine->>CalcEngine: Calculate base points
    CalcEngine->>CalcEngine: Calculate bonus points
    CalcEngine-->>PointService: Points to grant
    
    %% Record points
    PointService->>DB: Record point transaction
    DB-->>PointService: Record successful
    PointService->>DB: Update point balance
    DB-->>PointService: Update successful
    
    %% Update cache
    PointService->>Cache: Update latest point balance
    Cache-->>PointService: Update successful
    
    %% Point grant notification
    PointService->>EventBus: Point grant event
    EventBus->>Notification: Point grant notification
    Notification->>Notification: Generate notification
    Notification->>User: Notify user
    
    %% API response
    PointService-->>PointAPI: Point grant complete
    PointAPI-->>EventBus: Processing complete response
    
    %% Scheduled processing (separate sequence)
    Note over PointService, DB: Regular point expiry check (batch processing)
    PointService->>DB: Search expired points
    DB-->>PointService: Expired points
    PointService->>DB: Process point expiry
    DB-->>PointService: Processing complete
    PointService->>EventBus: Point expiry event
    EventBus->>Notification: Expiry notification
```

## Technology Stack

### Backend

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.2
- **Template Engine**: Thymeleaf (admin screens, email notifications, etc.)
- **Build Tool**: Gradle 8.5
- **API Documentation**: SpringDoc OpenAPI 2.3
- **Testing**: JUnit 5, Mockito, Testcontainers, Gatling
- **Data Access**:  
  - Spring Data JPA / Hibernate 6.4
  - Spring Data MongoDB
  - Spring Data Redis
  - QueryDSL (complex queries)
- **Messaging**: Apache Kafka, Spring Cloud Stream
- **Microservices Integration**:  
  - Spring Cloud 2023.0
  - Spring Cloud Gateway
  - Spring Cloud Config
  - Resilience4j (circuit breaker)
- **Monitoring**:  
  - Micrometer
  - Spring Boot Actuator
  - Azure Application Insights

### Frontend

- **Web Application**:
  - Framework: Next.js 14 (React)
  - Language: TypeScript 5
  - State Management: Redux Toolkit / Zustand
  - UI Library: TailwindCSS, Headless UI
  - API Integration: GraphQL (Apollo), Axios, React Query
  - Testing: Jest, React Testing Library, Cypress
  - i18n: next-intl (multi-language support)

- **Admin Interface**:
  - Spring Boot + Thymeleaf
  - Bootstrap 5
  - htmx (partial page updates)
  - Alpine.js (lightweight interactions)

### Data Store

- **Relational DB**: PostgreSQL 16
- **NoSQL DB**: MongoDB 7
- **Cache**: Redis 7.2
- **Search Engine**: Elasticsearch 8.12
- **Storage**: Azure Blob Storage
- **Message Broker**: Apache Kafka 3.6

### Infrastructure

- **Development Environment**:
  - Docker 25
  - Docker Compose v2
  - LocalStack (AWS service emulation)
  - Testcontainers (integration testing)

- **Production Environment**:
  - Azure Container Apps
  - Azure Container Registry
  - Azure Database for PostgreSQL
  - Azure Cache for Redis
  - Azure Cosmos DB (MongoDB API)
  - Azure Key Vault (secret management)
  - Azure Monitor & Application Insights
  - Azure Front Door & CDN (global distribution)

- **CI/CD**:
  - GitHub Actions
  - Terraform (IaC)
  - SonarQube (code quality)
  - Trivy (container security scanning)
- **Monitoring**: Prometheus, Grafana, Azure Monitor
- **Logging**: ELK Stack, Azure Log Analytics
- **Service Mesh**: Istio

### Cloud Services

- **Cloud Provider**: Microsoft Azure
- **API Management**: Azure API Management
- **AI/ML**: Azure AI Services, Azure OpenAI Service
- **CDN**: Azure CDN
- **Firewall**: Azure Firewall
- **Identity Management**: Azure AD B2C

## データモデル

### コアエンティティ関係図

```text
┌────────────────┐      ┌────────────────┐      ┌────────────────┐
│     User       │      │     Order      │      │    Product     │
├────────────────┤      ├────────────────┤      ├────────────────┤
│ id             │      │ id             │      │ id             │
│ email          │      │ userId         │◄─────┤ name           │
│ password       │      │ status         │      │ description    │
│ firstName      │      │ totalAmount    │      │ categoryId     │
│ lastName       │      │ createdAt      │      │ price          │
│ phoneNumber    │      │ updatedAt      │      │ stockQuantity  │
│ addresses      │      │ paymentId      │      │ attributes     │
│ pointBalance   │      │ couponId       │      │ images         │
│ createdAt      │      └────────┬───────┘      │ createdAt      │
│ updatedAt      │               │              │ updatedAt      │
└────────────────┘               │              └───────┬────────┘
       ▲                         │                      │
       │                         ▼                      │
┌──────┴───────┐      ┌────────────────┐               │
│  UserAddress  │      │   OrderItem    │◄──────────────┘
├───────────────┤      ├────────────────┤
│ id            │      │ id             │
│ userId        │      │ orderId        │
│ addressType   │      │ productId      │
│ recipient     │      │ quantity       │
│ zipCode       │      │ price          │
│ prefecture    │      │ discount       │
│ city          │      │ subtotal       │
│ streetAddress │      └────────────────┘
│ building      │               ▲
│ isDefault     │               │
└───────────────┘      ┌────────┴───────┐      ┌────────────────┐
                       │    Payment     │      │    Coupon      │
                       ├────────────────┤      ├────────────────┤
                       │ id             │      │ id             │
                       │ orderId        │      │ code           │
                       │ amount         │      │ description    │
                       │ method         │      │ discountType   │
                       │ status         │      │ discountValue  │
                       │ transactionId  │      │ minOrderAmount │
                       │ createdAt      │      │ startDate      │
                       └────────────────┘      │ endDate        │
                                               │ usageLimit     │
                                               │ isActive       │
                                               └────────────────┘
```

### Microservice Data Model Details

This section defines the detailed attributes and relationships of the main entities managed by each microservice.

#### User Management Service

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| User | Basic user information | id, email, passwordHash, firstName, lastName, phoneNumber, birthDate, createdAt, updatedAt, lastLoginAt, status |
| Role | User role | id, name, description, permissions |
| Permission | Fine-grained permission | id, name, description, resource, action |
| UserPreference | User settings | id, userId, language, currency, notificationPreferences, displayPreferences |
| Address | Shipping/Billing address | id, userId, addressType(SHIPPING/BILLING), recipient, zipCode, prefecture, city, streetAddress, building, phoneNumber, isDefault |
| UserActivity | User activity history | id, userId, activityType, timestamp, details, ipAddress, deviceInfo |

#### Inventory Management Service

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| Product | Basic product information | id, sku, name, description, brand, categoryId, price, cost, weight, dimensions, isActive, createdAt, updatedAt |
| Category | Product category | id, name, description, parentId, level, path, imageUrl, isActive |
| Inventory | Inventory information | id, productId, stockQuantity, reservedQuantity, availableQuantity, warehouseId, reorderLevel, updatedAt |
| Supplier | Supplier information | id, name, contactPerson, email, phone, address, rating, activeContractId |
| PriceHistory | Price history | id, productId, price, effectiveFrom, effectiveTo, promotionId |
| ProductAttribute | Product attribute | id, productId, attributeName, attributeValue, isFilterable, isSortable |
| ProductImage | Product image | id, productId, imageUrl, altText, sortOrder, isPrimary |

#### Sales Management Service

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| Order | Order information | id, userId, orderDate, status, subtotal, tax, shippingCost, discount, totalAmount, couponId, shippingAddressId, billingAddressId, paymentId, notes |
| OrderItem | Order item details | id, orderId, productId, productSnapshot, quantity, unitPrice, discount, tax, subtotal |
| Shipment | Shipping information | id, orderId, trackingNumber, carrier, status, shippingDate, estimatedDeliveryDate, actualDeliveryDate |
| Return | Return information | id, orderId, requestDate, status, reason, approvalDate, refundAmount, returnItemsData |
| Invoice | Invoice information | id, orderId, invoiceNumber, issuedDate, dueDate, paidDate, amount, status |
| OrderStatus | Order status | id, name, description, sequenceOrder |
| ShipmentStatus | Shipping status | id, name, description, sequenceOrder |

#### Payment & Cart Service

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| Cart | Cart information | id, userId, sessionId, createdAt, updatedAt, expiredAt, status |
| CartItem | Cart item | id, cartId, productId, quantity, addedAt, unitPrice, selectedAttributes |
| Payment | Payment information | id, orderId, amount, currency, method, status, gatewayReference, transactionDate |
| PaymentMethod | Payment method | id, userId, type, provider, accountReference, isDefault, expiryDate, billingAddressId |
| Transaction | Payment transaction | id, paymentId, type, amount, status, gatewayResponse, createdAt, updatedAt |
| PaymentStatus | Payment status | id, name, description, isSuccess, isProcessing, isFailed |

#### AI Support Service

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| UserInteraction | User interaction | id, userId, sessionId, startTime, endTime, interactionType |
| ChatSession | Chat session | id, userId, startTime, endTime, sessionSummary, feedbackRating |
| ChatMessage | Chat message | id, sessionId, timestamp, sender(USER/BOT), content, messageType |
| ProductRecommendation | Product recommendation history | id, userId, productId, recommendationSource, timestamp, wasClicked, confidence |
| SearchQuery | User search query | id, userId, query, timestamp, resultCount, filterParameters |
| BehaviorAnalysis | Behavior analysis data | id, userId, behaviorType, timestamp, details, sessionId |

#### Authentication Service (OAuth)

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| OAuthClient | OAuth authentication client | id, clientId, clientSecret, name, description, redirectUris, allowedGrantTypes, scopes |
| OAuthToken | Authentication token | id, accessToken, refreshToken, clientId, userId, scopes, issuedAt, expiresAt |
| OAuthScope | Permission scope | id, name, description, isDefault, required |
| OAuthConsent | User consent history | id, userId, clientId, scopes, consentedAt, expiresAt |
| MfaMethod | Multi-factor authentication method | id, userId, type(SMS/EMAIL/TOTP), status, createdAt, lastUsedAt |
| LoginAttempt | Login attempt history | id, userId, timestamp, ipAddress, userAgent, isSuccess, failureReason |

#### Coupon Service

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| Coupon | Coupon information | id, code, description, discountType(PERCENTAGE/FIXED), discountValue, minOrderAmount, startDate, endDate, usageLimit, usageCount, isActive |
| CouponType | Coupon type | id, name, description, usageLimitationType |
| CouponUsage | Coupon usage history | id, couponId, userId, orderId, usedAt, discountAmount |
| Campaign | Campaign information | id, name, description, startDate, endDate, status, budget, targetAudience, associatedCoupons |
| Promotion | Promotion information | id, name, description, startDate, endDate, type, conditions, applicationRules |
| CouponRestriction | Coupon restrictions | id, couponId, restrictionType(PRODUCT/CATEGORY/USER), restrictionValue, isExclusion |

#### Point Management Service

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| PointAccount | Point account | id, userId, balance, lifetimePoints, lastUpdatedAt |
| PointTransaction | Point transaction history | id, accountId, amount, type(EARN/REDEEM/EXPIRE/ADJUST), sourceType, sourceId, description, transactionDate, expiryDate |
| PointRule | Point grant rules | id, name, description, conversionRate, minimumAmount, applicableProducts, isActive |
| PointExpiry | Point expiry | id, accountId, amount, earnedDate, expiryDate, status |
| PointCampaign | Point campaign | id, name, description, multiplier, startDate, endDate, targetProducts, isActive |
| PointConversionRate | Point conversion rate | id, fromCurrency, toCurrency, rate, effectiveDate, expiryDate, isDefault |

#### Product Sales Website (Frontend)

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| Page | Page configuration | id, title, slug, metaDescription, metaKeywords, contentType, layout, status |
| ContentBlock | Content block | id, pageId, type, content, position, visibility, startDate, endDate |
| Navigation | Navigation structure | id, name, parentId, url, displayText, sortOrder, isActive, icon |
| SEOSetting | SEO settings | id, pageId, canonicalUrl, robots, structuredData, altLangLinks |
| MediaAsset | Media asset | id, type, url, fileSize, dimensions, format, altText, title, uploadedAt |
| Translation | Multi-language translation | id, entityType, entityId, language, field, translatedValue |

## API Design

### API Design Principles

- Adherence to RESTful design principles
- Consistent URI structure
- Proper use of HTTP methods
- Effective use of clear status codes
- Implementation of HATEOAS (Hypermedia as the Engine of Application State)
- Versioning strategy (URI, headers, media types)
- Unified error response format

### Common API Response Format

```json
{
  "status": "success | error | warning",
  "code": "RESOURCE_CREATED | RESOURCE_UPDATED | RESOURCE_DELETED | VALIDATION_ERROR | ...",
  "message": "Human-readable message",
  "data": { /* Response data */ },
  "meta": {
    "timestamp": "2025-01-01T12:00:00Z",
    "requestId": "req-123456",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 100,
      "totalPages": 5
    }
  },
  "_links": {
    "self": { "href": "/api/v1/resource/123" },
    "next": { "href": "/api/v1/resources?page=1&size=20" },
    "prev": null
  }
}
```

### Common Error Response Format

```json
{
  "status": "error",
  "code": "VALIDATION_ERROR",
  "message": "Input contains errors",
  "errors": [
    {
      "field": "email",
      "message": "Please enter a valid email address",
      "rejectedValue": "invalid-email"
    },
    {
      "field": "password",
      "message": "Password must be at least 8 characters",
      "rejectedValue": "1234"
    }
  ],
  "meta": {
    "timestamp": "2025-01-01T12:00:00Z",
    "requestId": "req-123456"
  }
}
```

### OpenAPI Specification Example (User Management Service)

```yaml
openapi: 3.0.3
info:
  title: Ski Shop User Management API
  description: User management microservice API for ski equipment sales site
  version: 1.0.0
servers:
  - url: https://api.skieshop.com/users
    description: User Management Service Base URL
paths:
  /users:
    get:
      summary: Get user list
      description: Retrieve list of users in the system
      parameters:
        - name: page
          in: query
          description: Page number
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          description: Page size
          schema:
            type: integer
            default: 20
      responses:
        '200':
          description: Success
          content:
            application/json:
              schema:
                type: object
                properties:
                  content:
                    type: array
                    items:
                      $ref: '#/components/schemas/UserSummary'
                  pageable:
                    $ref: '#/components/schemas/PageInfo'
    post:
      summary: Create new user
      description: Create a new user
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UserCreationRequest'
      responses:
        '201':
          description: User creation successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/User'
        '400':
          description: Invalid request
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /users/{id}:
    get:
      summary: Get user details
      description: Retrieve details of user with specified ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Success
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/User'
        '404':
          description: User not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
    put:
      summary: Update user information
      description: Update information for user with specified ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UserUpdateRequest'
      responses:
        '200':
          description: Update successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/User'
        '400':
          description: Invalid request
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '404':
          description: User not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
    delete:
      summary: Delete user
      description: Delete user with specified ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Deletion successful
        '404':
          description: User not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

components:
  schemas:
    User:
      type: object
      properties:
        id:
          type: string
          format: uuid
        email:
          type: string
          format: email
        firstName:
          type: string
        lastName:
          type: string
        phoneNumber:
          type: string
        addresses:
          type: array
          items:
            $ref: '#/components/schemas/Address'
        pointBalance:
          type: integer
        roles:
          type: array
          items:
            type: string
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time
      required:
        - id
        - email
        - firstName
        - lastName
        - roles
        - createdAt
        - updatedAt
```

### OpenAPI仕様例（在庫管理サービス）

```yaml
openapi: 3.0.3
info:
  title: スキーショップ在庫管理API
  description: スキー用品販売サイトの在庫管理マイクロサービスAPI
  version: 1.0.0
servers:
  - url: https://api.skieshop.com/inventory
    description: 在庫管理サービスベースURL
paths:
  /products:
    get:
      summary: 商品一覧取得
      description: 商品一覧を取得します（フィルタリング・ソート・ページング対応）
      parameters:
        - name: category
          in: query
          description: カテゴリID
          schema:
            type: string
        - name: query
          in: query
          description: 検索キーワード
          schema:
            type: string
        - name: minPrice
          in: query
          description: 最低価格
          schema:
            type: number
        - name: maxPrice
          in: query
          description: 最高価格
          schema:
            type: number
        - name: inStock
          in: query
          description: 在庫あり商品のみ
          schema:
            type: boolean
        - name: sort
          in: query
          description: ソート項目（price, name, popularity）
          schema:
            type: string
            enum: [price_asc, price_desc, name_asc, name_desc, popularity_desc]
        - name: page
          in: query
          description: ページ番号
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          description: ページサイズ
          schema:
            type: integer
            default: 20
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                type: object
                properties:
                  content:
                    type: array
                    items:
                      $ref: '#/components/schemas/ProductSummary'
                  pageable:
                    $ref: '#/components/schemas/PageInfo'
    post:
      summary: 新規商品登録
      description: 新しい商品を登録します
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ProductCreationRequest'
      responses:
        '201':
          description: 商品登録成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Product'
        '400':
          description: 無効なリクエスト
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /products/{id}/inventory:
    get:
      summary: 商品在庫情報取得
      description: 指定商品の在庫情報を取得します
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/InventoryInfo'
        '404':
          description: 商品が見つかりません
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
    put:
      summary: 在庫数更新
      description: 指定商品の在庫数を更新します
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/InventoryUpdateRequest'
      responses:
        '200':
          description: 更新成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/InventoryInfo'
        '400':
          description: 無効なリクエスト
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
```

### GraphQL API例（検索・フィルタリング向け）

```graphql
type Query {
  # 商品検索
  products(
    query: String
    category: ID
    priceRange: PriceRangeInput
    attributes: [AttributeFilterInput!]
    sort: ProductSortInput
    pagination: PaginationInput
  ): ProductConnection!
  
  # 商品詳細
  product(id: ID!): Product
  
  # カテゴリ一覧
  categories(parent: ID): [Category!]!
}

type Product {
  id: ID!
  name: String!
  description: String
  price: Float!
  discount: Float
  finalPrice: Float!
  brand: String
  category: Category!
  images: [ProductImage!]!
  attributes: [ProductAttribute!]!
  inventory: InventoryInfo!
  reviews: ReviewConnection!
  relatedProducts: [Product!]!
}

type ProductConnection {
  edges: [ProductEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
  facets: [FacetResult!]!
}

type ProductEdge {
  node: Product!
  cursor: String!
}

type PageInfo {
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
  startCursor: String
  endCursor: String
}

type FacetResult {
  name: String!
  values: [FacetValue!]!
}

type FacetValue {
  value: String!
  count: Int!
  selected: Boolean!
}

input PriceRangeInput {
  min: Float
  max: Float
}

input AttributeFilterInput {
  name: String!
  values: [String!]!
}

input ProductSortInput {
  field: ProductSortField!
  direction: SortDirection!
}

enum ProductSortField {
  PRICE
  NAME
  POPULARITY
  NEWEST
  RATING
}

enum SortDirection {
  ASC
  DESC
}

input PaginationInput {
  first: Int
  after: String
  last: Int
  before: String
}
```

### API間連携パターン

- **同期通信**:
  - REST API (JSON/HTTP)
  - GraphQL (クライアント用、複合クエリ)
  - gRPC (内部サービス間高速通信)

- **非同期通信**:
  - イベントストリーミング (Apache Kafka)
  - メッセージキュー (RabbitMQ)
  - Webhook (外部システム連携)

### イベントペイロード例（JSON）

```json
{
  "eventId": "e37d0b80-c74a-4da5-b4e5-1b0db6c3f62a",
  "eventType": "OrderCreated",
  "timestamp": "2025-06-19T13:45:30.123Z",
  "version": "1.0",
  "payload": {
    "orderId": "ord-12345",
    "userId": "usr-67890",
    "items": [
      {
        "productId": "prod-12345",
        "quantity": 1,
        "price": 39800
      }
    ],
    "totalAmount": 39800,
    "status": "CREATED"
  }
}
```

### gRPC サービス定義例（在庫確認サービス）

```protobuf
syntax = "proto3";

package com.skieshop.inventory;

service InventoryService {
  // 単一商品の在庫状況確認
  rpc CheckStock (CheckStockRequest) returns (StockResponse);
  
  // 複数商品の在庫状況一括確認
  rpc CheckStockBatch (CheckStockBatchRequest) returns (StockBatchResponse);
  
  // 在庫状況のリアルタイム更新をサブスクライブ
  rpc SubscribeStockUpdates (StockSubscriptionRequest) returns (stream StockUpdate);
}

message CheckStockRequest {
  string product_id = 1;
  int32 required_quantity = 2;
}

message StockResponse {
  string product_id = 1;
  int32 available_quantity = 2;
  bool is_in_stock = 3;
  bool can_fulfill_request = 4;
  string estimated_restock_date = 5; // ISO8601形式、在庫切れの場合のみ
}

message CheckStockBatchRequest {
  repeated CheckStockRequest items = 1;
}

message StockBatchResponse {
  repeated StockResponse items = 1;
  bool all_items_available = 2;
}

message StockSubscriptionRequest {
  repeated string product_ids = 1;
  bool include_price_updates = 2;
}

message StockUpdate {
  string product_id = 1;
  int32 available_quantity = 2;
  float current_price = 3;
  string update_timestamp = 4;
  StockUpdateType update_type = 5;
}

enum StockUpdateType {
  QUANTITY_CHANGED = 0;
  PRICE_CHANGED = 1;
  PRODUCT_UNAVAILABLE = 2;
  PRODUCT_AVAILABLE = 3;
}
```

## Authentication & Authorization

### Authentication Architecture

This system implements a comprehensive authentication and authorization framework based on OAuth 2.0 and OpenID Connect protocols. By combining Azure AD B2C and Keycloak, we build a flexible and secure identity platform.

#### Authentication Component Architecture

```mermaid
graph TB
    User[User] --> WebApp[Web Application]
    User --> MobileApp[Mobile App]
    
    WebApp --> APIGW[API Gateway]
    MobileApp --> APIGW
    
    subgraph "Authentication Infrastructure"
        APIGW --> |Authentication Request| AuthService[Authentication Service]
        AuthService <--> |OIDC/OAuth2.0| IdP[ID Provider - Keycloak/Azure AD B2C]
        AuthService --> TokenService[Token Management Service]
        IdP <--> UserDB[(User DB)]
    end
    
    APIGW --> |Valid Token| MicroService1[Microservice 1]
    APIGW --> |Valid Token| MicroService2[Microservice 2]
    
    subgraph "External Authentication"
        IdP <--> |OIDC/OAuth| Google[Google]
        IdP <--> |OIDC/OAuth| Facebook[Facebook]
        IdP <--> |OIDC/OAuth| Apple[Apple]
        IdP <--> |OIDC/OAuth| LINE[LINE]
    end
```

### Authentication Flows

#### 1. OAuth 2.0/OpenID Connect Authentication Flow

- **Authorization Code Flow (Web Applications)**:
  1. User requests login
  2. Application redirects user to authorization endpoint
  3. User authenticates and provides consent
  4. Authorization server issues authorization code
  5. Application exchanges authorization code for tokens via back-channel
  6. Application obtains ID token and access token

- **Authorization Code Flow with PKCE (Mobile Apps)**:
  - Adds protection via Code Verifier and code_challenge to authorization code flow
  - No need to store client secret in mobile app

- **Client Credentials Flow (Service-to-Service Communication)**:
  - Direct token acquisition using client ID and secret
  - Used for system-to-system API calls without user context

- **Resource Owner Password Credentials Flow (Legacy System Integration)**:
  - Token acquisition directly using username and password
  - Limited use only for legacy system integration

#### 2. Social Login Integration

- Google
- Facebook
- Apple
- LINE
- Twitter

Social login is implemented using standard OpenID Connect/OAuth flows and integrated through the identity provider (Azure AD B2C/Keycloak). Each social ID is mapped to an internal user profile.

#### 3. Multi-Factor Authentication (MFA)

- **SMS One-Time Password**:
  - One-time code sent to user's phone number
  - Integration with services like Twilio

- **Email Authentication Code**:
  - Temporary code sent to user's email address
  - Integration with SendGrid

- **TOTP (Time-based One-Time Password)**:
  - Support for authentication apps like Google Authenticator, Microsoft Authenticator
  - RFC 6238 compliant

- **Push Notifications**:
  - Approval via push notifications to mobile app
  - Uses Firebase Cloud Messaging (FCM)

### Authentication Flow Diagram

```mermaid
sequenceDiagram
    participant User as User
    participant WebApp as Web Application
    participant APIGW as API Gateway
    participant Auth as Authentication Service
    participant IdP as ID Provider
    participant MS as Microservice
    
    User->>WebApp: 1. Login request
    WebApp->>IdP: 2. Authentication request
    IdP->>User: 3. Display login screen
    User->>IdP: 4. Enter credentials
    
    alt MFA enabled
        IdP->>User: 5a. Second factor authentication request
        User->>IdP: 5b. Second factor authentication response
    end
    
    IdP->>WebApp: 6. Return authorization code
    WebApp->>Auth: 7. Exchange authorization code for tokens
    Auth->>WebApp: 8. Issue ID token and access token
    
    User->>WebApp: 9. Access protected resource
    WebApp->>APIGW: 10. API request + access token
    APIGW->>APIGW: 11. Token validation
    APIGW->>MS: 12. Forward validated request
    MS->>APIGW: 13. Response
    APIGW->>WebApp: 14. API response
    WebApp->>User: 15. Display result
```

### Authorization Model

#### Role-Based Access Control (RBAC)

| Role Name | Description | Main Permissions |
|-----------|-------------|------------------|
| Customer | General customer | Product browsing, order creation, own profile management |
| PremiumCustomer | Premium member | Customer permissions + special offers viewing, early access |
| StoreAdmin | Store administrator | Order management, customer support, basic product management |
| InventoryManager | Inventory manager | Product/inventory management, pricing, procurement management |
| SalesManager | Sales manager | Sales report viewing, campaign management, discount settings |
| SystemAdmin | System administrator | Access to all functions, user management, system settings |

#### Attribute-Based Access Control (ABAC)

In addition to RBAC (role-based), we implement fine-grained access control based on the following attributes:

1. **User Attributes**:
   - Membership status (regular/silver/gold/platinum)
   - Purchase history (total purchase amount, purchase frequency)
   - Region (prefecture, country)
   - Age group

2. **Resource Attributes**:
   - Product category (general/limited/premium products)
   - Content type (general/member-only/promotional)
   - Data confidentiality level

3. **Environment Attributes**:
   - Access time
   - Source IP address
   - Device type

### JWT構造

#### アクセストークン

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-id-1"
}
{
  "iss": "https://auth.skieshop.com",
  "sub": "user-123456",
  "aud": "ski-shop-api",
  "exp": 1609459200,
  "iat": 1609455600,
  "auth_time": 1609455600,
  "azp": "web-client-123",
  "scope": "openid profile email api:read api:write",
  "roles": ["Customer", "PremiumCustomer"],
  "permissions": ["products:read", "orders:create", "profile:write"],
  "amr": ["pwd", "mfa"],
  "jti": "abc-123-xyz-789"
}
```

#### IDトークン

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-id-1"
}
{
  "iss": "https://auth.skieshop.com",
  "sub": "user-123456",
  "aud": "web-client-123",
  "exp": 1609459200,
  "iat": 1609455600,
  "auth_time": 1609455600,
  "nonce": "n-0S6_WzA2M",
  "name": "山田 太郎",
  "given_name": "太郎",
  "family_name": "山田",
  "email": "taro.yamada@example.com",
  "email_verified": true,
  "picture": "https://profile.skieshop.com/photos/user-123456.jpg",
  "locale": "ja-JP",
  "preferred_username": "taro.yamada",
  "amr": ["pwd", "mfa"]
}
```

### セキュリティ実装

#### トランスポート層セキュリティ

- **TLS 1.3の強制**:
  - 全通信での最新TLSプロトコル使用
  - 古いTLSバージョン（1.0/1.1/1.2）の無効化

- **適切な暗号スイートの設定**:

  ```text
  TLS_AES_256_GCM_SHA384
  TLS_AES_128_GCM_SHA256
  TLS_CHACHA20_POLY1305_SHA256
  ```

- **HSTS（HTTP Strict Transport Security）の実装**:
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload`
  - HTTPS強制とダウングレード攻撃防止

#### APIセキュリティ

- **JWT検証**:
  - 署名検証（RS256アルゴリズム）
  - 有効期限チェック
  - 発行者・対象者検証
  - スコープと権限の検証

- **トークンの有効期限と更新メカニズム**:
  - アクセストークン: 短期（1時間）
  - リフレッシュトークン: 長期（14日）
  - トークンローテーション

- **CSRFトークン保護**:
  - ステートフルなセッションでのCSRFトークン要求
  - Double Submit Cookie検証

- **レート制限とスロットリング**:
  - IPアドレスベース: 1分あたり60リクエスト
  - APIキーベース: 1分あたり300リクエスト
  - ユーザーベース: 1分あたり120リクエスト

#### データ保護

- **個人識別情報（PII）の暗号化**:
  - 保存時: AES-256-GCM
  - 転送時: TLS 1.3
  - データベース列レベルの暗号化

- **トークン保護**:
  - ブラウザ: HttpOnly & Secure Cookies
  - モバイル: セキュアストレージ
  - CSRFトークンの実装

- **機密データのマスキング**:
  - クレジットカード: 最初の6桁と最後の4桁のみ表示
  - 電話番号: 一部をアスタリスクに置換
  - ログからの機密情報除外

## インフラストラクチャ設計

### クラウドアーキテクチャ

Microsoft Azureをメインクラウドプロバイダーとして使用し、特にAzure Container Appsを中心に構成します：

- **コンピューティング**:
  - Azure Container Apps: 各マイクロサービスのコンテナホスティング
  - Azure Container Registry: コンテナイメージのプライベートレジストリ
  - Azure Functions: イベント駆動型処理、バッチ処理、スケジュールタスク

- **ネットワーキング**:
  - Azure Virtual Network: プライベートネットワーク構成
  - Azure Front Door + CDN: グローバル配信とキャッシング
  - Azure Application Gateway: Webアプリケーションファイアウォール、TLS終端
  - Azure API Management: API公開と管理

- **データストア**:
  - Azure Database for PostgreSQL: リレーショナルデータ
  - Azure Cosmos DB (MongoDB API): 商品カタログ、非構造化データ
  - Azure Cache for Redis: セッション管理、キャッシュ
  - Azure Storage: 画像、静的コンテンツ、バックアップ

- **セキュリティ**:
  - Azure AD B2C: 顧客ID管理
  - Azure Key Vault: シークレット管理
  - Azure Security Center: セキュリティ監視と対応
  - Azure Defender for Container: コンテナセキュリティ

- **監視と運用**:
  - Azure Monitor: アプリケーション監視
  - Azure Log Analytics: ログ集約と分析
  - Azure Application Insights: アプリケーションパフォーマンス監視
  - Azure Automation: 運用タスクの自動化

### Azure Container Appsデプロイメント構成

```mermaid
graph TB
    subgraph "Azure"
        subgraph "フロントエンド"
            AFD[Azure Front Door/CDN]
            WAF[Application Gateway/WAF]
        end
        
        subgraph "API管理"
            APIM[Azure API Management]
        end
        
        subgraph "Azure Container Apps環境"
            CAENV[Container Apps環境]
            
            subgraph "マイクロサービス"
                CAAPI[API Gateway App]
                CAWEB[Web Frontend App]
                CAINV[Inventory Service App]
                CAUSER[User Management App]
                CASALES[Sales Management App]
                CAAI[AI Support App]
                CAPAY[Payment App]
                CACOUPON[Coupon App]
                CAPOINT[Point App]
                CAAUTH[OAuth Service App]
            end
        end
        
        subgraph "マネージドサービス"
            PSQL[Azure DB for PostgreSQL]
            COSMOS[Azure Cosmos DB]
            REDIS[Azure Cache for Redis]
            BLOB[Azure Blob Storage]
            EVENTHUB[Azure Event Hubs]
        end
        
        subgraph "認証・セキュリティ"
            ADB2C[Azure AD B2C]
            KV[Key Vault]
        end
        
        subgraph "監視"
            MONITOR[Azure Monitor]
            INSIGHTS[Application Insights]
            LOGS[Log Analytics]
        end
    end
    
    %% 接続
    AFD --> WAF
    WAF --> APIM
    APIM --> CAAPI
    
    %% Container Apps接続
    CAAPI --> CAWEB
    CAAPI --> CAINV
    CAAPI --> CAUSER
    CAAPI --> CASALES
    CAAPI --> CAAI
    CAAPI --> CAPAY
    CAAPI --> CACOUPON
    CAAPI --> CAPOINT
    CAAPI --> CAAUTH
    
    %% マネージドサービス接続
    CAINV --> COSMOS
    CAINV --> BLOB
    CAUSER --> PSQL
    CASALES --> PSQL
    CAPAY --> PSQL
    CACOUPON --> REDIS
    CAPOINT --> REDIS
    CAAI --> COSMOS
    CAWEB --> BLOB
    
    %% イベントベース接続
    CAINV -.-> EVENTHUB
    CAUSER -.-> EVENTHUB
    CASALES -.-> EVENTHUB
    CAPAY -.-> EVENTHUB
    CACOUPON -.-> EVENTHUB
    CAPOINT -.-> EVENTHUB
    
    %% 認証接続
    CAAPI --> ADB2C
    CAAUTH --> ADB2C
    CAUSER --> KV
    CAPAY --> KV
    
    %% 監視接続
    CAAPI -.-> INSIGHTS
    CAWEB -.-> INSIGHTS
    CAINV -.-> INSIGHTS
    CAUSER -.-> INSIGHTS
    CASALES -.-> INSIGHTS
    CAAI -.-> INSIGHTS
    CAPAY -.-> INSIGHTS
    CACOUPON -.-> INSIGHTS
    CAPOINT -.-> INSIGHTS
    CAAUTH -.-> INSIGHTS
    
    INSIGHTS --> MONITOR
    INSIGHTS --> LOGS
```

### ローカル開発環境（Docker Compose）

ローカル開発では、Docker Composeを使用して各マイクロサービスとその依存関係を簡単に起動できるようにします。

```mermaid
graph TB
    subgraph "Docker Compose 環境"
        subgraph "フロントエンドサービス"
            NGINX[NGINX]
            WEBCLIENT[Next.js Webフロントエンド]
            ADMIN[管理画面アプリ]
        end
        
        subgraph "バックエンドサービス"
            APIGATEWAY[API Gateway]
            USERSERVICE[ユーザー管理サービス]
            INVENTORYSERVICE[在庫管理サービス]
            SALESSERVICE[販売管理サービス]
            AISERVICE[AIサポートサービス]
            PAYMENTSERVICE[支払い・カートサービス]
            COUPONSERVICE[クーポンサービス]
            POINTSERVICE[ポイントサービス]
            AUTHSERVICE[認証サービス]
        end
        
        subgraph "データベース"
            POSTGRES[PostgreSQL]
            MONGODB[MongoDB]
            REDISDB[Redis]
            ELASTICSEARCH[Elasticsearch]
        end
        
        subgraph "インフラサービス"
            KAFKA[Kafka & Zookeeper]
            SCHEMAREGISTRY[Schema Registry]
            KEYCLOAK[Keycloak]
            MAILHOG[MailHog]
            MINIO[MinIO]
        end
        
        subgraph "モニタリング"
            PROMETHEUS[Prometheus]
            GRAFANA[Grafana]
            ZIPKIN[Zipkin]
        end
    end
    
    %% 接続
    NGINX --> WEBCLIENT
    NGINX --> ADMIN
    NGINX --> APIGATEWAY
    
    APIGATEWAY --> USERSERVICE
    APIGATEWAY --> INVENTORYSERVICE
    APIGATEWAY --> SALESSERVICE
    APIGATEWAY --> AISERVICE
    APIGATEWAY --> PAYMENTSERVICE
    APIGATEWAY --> COUPONSERVICE
    APIGATEWAY --> POINTSERVICE
    APIGATEWAY --> AUTHSERVICE
    
    USERSERVICE --> POSTGRES
    SALESSERVICE --> POSTGRES
    PAYMENTSERVICE --> POSTGRES
    
    INVENTORYSERVICE --> MONGODB
    AISERVICE --> MONGODB
    
    COUPONSERVICE --> REDISDB
    POINTSERVICE --> REDISDB
    
    INVENTORYSERVICE --> ELASTICSEARCH
    
    USERSERVICE -.-> KAFKA
    INVENTORYSERVICE -.-> KAFKA
    SALESSERVICE -.-> KAFKA
    PAYMENTSERVICE -.-> KAFKA
    COUPONSERVICE -.-> KAFKA
    POINTSERVICE -.-> KAFKA
    
    AUTHSERVICE --> KEYCLOAK
    
    USERSERVICE --> MAILHOG
    SALESSERVICE --> MAILHOG
    
    INVENTORYSERVICE --> MINIO
    AISERVICE --> MINIO
    
    USERSERVICE -.-> PROMETHEUS
    INVENTORYSERVICE -.-> PROMETHEUS
    SALESSERVICE -.-> PROMETHEUS
    AISERVICE -.-> PROMETHEUS
    PAYMENTSERVICE -.-> PROMETHEUS
    COUPONSERVICE -.-> PROMETHEUS
    POINTSERVICE -.-> PROMETHEUS
    AUTHSERVICE -.-> PROMETHEUS
    
    PROMETHEUS --> GRAFANA
    
    USERSERVICE -.-> ZIPKIN
    INVENTORYSERVICE -.-> ZIPKIN
    SALESSERVICE -.-> ZIPKIN
    AISERVICE -.-> ZIPKIN
    PAYMENTSERVICE -.-> ZIPKIN
    COUPONSERVICE -.-> ZIPKIN
    POINTSERVICE -.-> ZIPKIN
    AUTHSERVICE -.-> ZIPKIN
```

### サンプルDocker Compose設定

主要なサービスの構成を示す`docker-compose.yml`ファイルの例:

```yaml
version: '3.8'

services:
  # インフラサービス
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: skieshop
      POSTGRES_PASSWORD: skieshop
      POSTGRES_DB: skieshop
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./docker/postgres/init-scripts:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U skieshop"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:7
    environment:
      MONGO_INITDB_ROOT_USERNAME: skieshop
      MONGO_INITDB_ROOT_PASSWORD: skieshop
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
      - ./docker/mongo/init-scripts:/docker-entrypoint-initdb.d

  redis:
    image: redis:7.2-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  # マイクロサービス
  api-gateway:
    build:
      context: ./api-gateway
      dockerfile: Dockerfile.dev
    ports:
      - "8080:8080"
    depends_on:
      - auth-service
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SERVER_PORT: 8080
      SPRING_CLOUD_GATEWAY_ROUTES_0_URI: http://user-service:8081
      SPRING_CLOUD_GATEWAY_ROUTES_0_PREDICATES_0: Path=/api/users/**
      # 他のルーティング設定...

  user-service:
    build:
      context: ./user-service
      dockerfile: Dockerfile.dev
    ports:
      - "8081:8081"
    depends_on:
      - postgres
      - kafka
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SERVER_PORT: 8081
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/skieshop_users
      SPRING_DATASOURCE_USERNAME: skieshop
      SPRING_DATASOURCE_PASSWORD: skieshop
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      # 他の環境変数...

  inventory-service:
    build:
      context: ./inventory-service
      dockerfile: Dockerfile.dev
    ports:
      - "8082:8082"
    depends_on:
      - mongodb
      - kafka
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SERVER_PORT: 8082
      SPRING_DATA_MONGODB_URI: mongodb://skieshop:skieshop@mongodb:27017/inventory
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      # 他の環境変数...

  # フロントエンド
  web-frontend:
    build:
      context: ./web-frontend
      dockerfile: Dockerfile.dev
    ports:
      - "3000:3000"
    volumes:
      - ./web-frontend:/app
      - /app/node_modules
    environment:
      NODE_ENV: development
      NEXT_PUBLIC_API_URL: http://localhost:8080/api

  # モニタリングツール
  prometheus:
    image: prom/prometheus:v2.45.0
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus

  grafana:
    image: grafana/grafana:10.2.0
    ports:
      - "3100:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./docker/grafana/provisioning:/etc/grafana/provisioning
      - grafana-data:/var/lib/grafana
    depends_on:
      - prometheus

volumes:
  postgres-data:
  mongo-data:
  redis-data:
  prometheus-data:
  grafana-data:
```

### 環境構成

- **開発環境**:
  - Docker Compose（上記構成）
  - ローカルJDK 21
  - ローカルIDEと開発ツール
  - モックサービス

- **テスト環境**:
  - Azure Container Apps（小規模構成）
  - Azure Database for PostgreSQL
  - Azure Cache for Redis
  - Azure Cosmos DB (MongoDB API)
  - テスト用データセット
  - 自動テスト統合

- **ステージング環境**:
  - 本番環境と同様の構成（小規模）
  - 本番データのサブセットまたは匿名化データ
  - 本番と同一のネットワークポリシー
  - 手動およびカナリアテスト

- **本番環境**:
  - Azure Container Apps（自動スケーリング設定）
  - リージョンレプリケーション
  - マネージドサービス優先
  - 本番データと完全なセキュリティ対策

### Azure Container Appsリソース設定

各マイクロサービスのコンテナアプリ設定例:

```yaml
name: inventory-service
resourceGroup: ski-shop-prod
location: japaneast
environmentId: /subscriptions/your-subscription-id/resourceGroups/ski-shop-prod/providers/Microsoft.App/managedEnvironments/ski-shop-env
configuration:
  activeRevisionsMode: Multiple
  ingress:
    external: true
    targetPort: 8080
    transport: http
    corsPolicy:
      allowedOrigins: ["https://www.skieshop.com"]
      allowedMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
      allowedHeaders: ["*"]
      maxAge: 3600
  dapr:
    enabled: true
    appId: inventory-service
    appPort: 8080
  registries:
    - server: skieshopacr.azurecr.io
      identity: system
  secrets:
    - name: mongodb-connection-string
      keyVaultUrl: https://ski-shop-kv.vault.azure.net/secrets/mongodb-connection-string
  
template:
  containers:
    - image: skieshopacr.azurecr.io/inventory-service:latest
      name: inventory-service
      env:
        - name: SPRING_PROFILES_ACTIVE
          value: prod
        - name: APPLICATIONINSIGHTS_CONNECTION_STRING
          value: InstrumentationKey=your-instrumentation-key
        - name: SPRING_DATA_MONGODB_URI
          secretRef: mongodb-connection-string
      resources:
        cpu: 1.0
        memory: 2Gi
      probes:
        - type: liveness
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        - type: readiness
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 15
          periodSeconds: 5
  scale:
    minReplicas: 1
    maxReplicas: 10
    rules:
      - name: http-scale-rule
        http:
          metadata:
            concurrentRequests: "100"
      - name: cpu-scale-rule
        custom:
          type: cpu
          metadata:
            type: Utilization
            value: "70"
```

### ディザスタリカバリ戦略

1. **データバックアップ**:
   - フルバックアップ: 日次
   - 増分バックアップ: 時間単位
   - Point-in-Timeリカバリー対応

2. **マルチリージョン戦略**:
   - アクティブ-パッシブ構成
   - 地理的に分散したリージョン（東日本・西日本）
   - データレプリケーション

3. **リカバリー手順**:
   - 自動フェイルオーバー
   - 手動リージョン切り替えプロセス
   - 定期的なDRテスト（四半期ごと）

4. **RPO/RTO目標**:
   - RPO（目標復旧時点）: 1時間
   - RTO（目標復旧時間）: 4時間

## 非機能要件

### パフォーマンス要件

- **レスポンスタイム**:
  - APIエンドポイント: 95%のリクエストが300ms以内
  - ページロード時間: 95%のページが2秒以内
  - 画像読み込み: 1秒以内
  - 検索結果表示: 500ms以内

- **スループット**:
  - ピーク時: 1000 TPS（トランザクション/秒）
  - 通常時: 200 TPS
  - バッチ処理: 1時間あたり10万レコード

- **キャパシティ**:
  - 同時ユーザー: 10,000人
  - 製品データ: 10万SKU
  - 注文データ: 日間5,000件
  - メディアストレージ: 初期5TB、年間成長2TB

### スケーラビリティ要件

- **水平スケーリング**:
  - CPU使用率70%でのオートスケーリング
  - ゼロダウンタイムスケーリング
  - インスタンス起動時間30秒以内

- **データスケーリング**:
  - シャーディング対応データモデル
  - 効率的なインデックス設計
  - コールドデータのアーカイブ戦略

### 可用性要件

- **サービスレベル目標（SLO）**:
  - Webサイト: 99.95%（月間21.6分のダウンタイム許容）
  - API: 99.99%（月間4.3分のダウンタイム許容）
  - バックオフィス機能: 99.9%（月間43.2分のダウンタイム許容）

- **障害対策**:
  - マルチAZ配置
  - サーキットブレーカー実装
  - 自動フェイルオーバー
  - グレースフルデグラデーション

- **バックアップ/リカバリ**:
  - フルバックアップ: 日次
  - 増分バックアップ: 時間単位
  - RPO（目標復旧時点）: 1時間
  - RTO（目標復旧時間）: 4時間

### セキュリティ要件

- **コンプライアンス**:
  - PCI DSS（決済カード業界データセキュリティ基準）
  - GDPR（EU一般データ保護規則）
  - 日本の個人情報保護法

- **データ保護**:
  - 保存データの暗号化（AES-256）
  - 転送データの暗号化（TLS 1.3）
  - 個人情報の仮名化または匿名化
  - データアクセス監査

- **脆弱性管理**:
  - 定期的な脆弱性スキャン（週次）
  - ペネトレーションテスト（四半期ごと）
  - 依存関係の脆弱性監視（OWASP依存関係チェック）
  - セキュリティパッチ適用SLA: 重大な脆弱性は24時間以内

### 運用性要件

- **監視**:
  - リアルタイムパフォーマンスダッシュボード
  - カスタムアラートと通知
  - SLO/SLAモニタリング
  - ユーザー体験監視

- **ロギング**:
  - 構造化ログ（JSON形式）
  - 集中ログ管理
  - ログ保持: 運用ログ30日、セキュリティログ1年
  - ログデータ匿名化

- **障害管理**:
  - インシデント対応プロセス
  - 自動アラート
  - エスカレーションパス
  - 障害分析と再発防止

## 開発・運用プロセス

### 開発ライフサイクル

- **計画・設計**:
  - 要件分析とユーザーストーリー作成
  - アーキテクチャ設計レビュー
  - 技術的な検証（PoC）
  - タスク分解とバックログ作成

- **開発**:
  - トランクベース開発
  - 継続的インテグレーション
  - ペアプログラミング/モブプログラミング
  - コードレビュー

- **テスト**:
  - 自動単体テスト（JUnit, Jest）
  - 統合テスト（Testcontainers, Spring Test）
  - コントラクトテスト（Spring Cloud Contract）
  - E2Eテスト（Cypress, Selenium）
  - パフォーマンステスト（Gatling, JMeter）

- **デプロイ**:
  - 継続的デリバリー
  - 自動化されたカナリアリリース
  - ブルー/グリーンデプロイメント
  - フィーチャーフラグによる機能リリース

### CI/CDパイプライン

```text
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│         │    │         │    │         │    │         │    │         │
│  コード  │───►│  ビルド  │───►│  テスト  │───►│ 品質検査 │───►│イメージ作成│
│ コミット │    │         │    │         │    │         │    │         │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └────┬────┘
                                                                  │
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐         │
│         │    │         │    │         │    │         │         │
│  本番    │◄───│ステージング│◄───│  テスト  │◄───│  開発   │◄────────┘
│ デプロイ │    │ デプロイ │    │ デプロイ │    │ デプロイ │
└─────────┘    └─────────┘    └─────────┘    └─────────┘
```

### 運用プロセス

- **リリース管理**:
  - リリースカレンダー
  - 変更管理プロセス
  - リリースノート自動生成
  - ロールバック手順

- **インシデント管理**:
  - インシデント検出と分類
  - エスカレーションプロセス
  - 解決と根本原因分析
  - ポストモーテムとナレッジベース更新

- **パフォーマンス管理**:
  - 定期的なパフォーマンステスト
  - ボトルネック分析
  - 最適化サイクル
  - 容量計画

- **変更管理**:
  - リリース計画
  - リスク評価
  - 変更承認プロセス
  - 変更適用とモニタリング

## 移行戦略

### フェーズド実装アプローチ

マイクロサービスアーキテクチャへの移行は、以下の4つのフェーズで実施します。この段階的アプローチにより、リスクを最小化しながら継続的に価値を提供することが可能になります。

#### フェーズ1: 基盤構築（3ヶ月）

- クラウドインフラストラクチャセットアップ（Azure環境構築）
- CI/CDパイプライン確立（GitHub Actions/Azure DevOps）
- コアマイクロサービスの骨格実装（API定義、DB設計）
- 認証基盤の構築（Azure AD B2C、Keycloak）
- 開発環境と基本テスト環境の整備
- 技術検証（PoC）の実施

#### フェーズ2: コア機能開発（4ヶ月）

- 製品カタログと在庫管理サービスの実装
- ユーザー管理とプロファイルサービスの実装
- 検索機能とWeb UIの基本実装
- 注文処理の基本フローの実装
- APIゲートウェイの実装と統合
- 基本的な監視とロギング機能の実装

#### フェーズ3: 拡張機能（3ヶ月）

- 支払い処理の完全統合
- ポイントシステム実装
- クーポン機能実装
- AIレコメンデーション機能実装
- 多言語対応の完成
- 高度な検索とフィルタリング機能

#### フェーズ4: 最適化とスケーリング（2ヶ月）

- パフォーマンス最適化
- セキュリティ強化とペネトレーションテスト
- 高可用性構成の完成
- 本番環境の完全スケーリング
- 運用ツールとダッシュボードの整備
- マルチリージョン対応

### ストラングラーフィグパターンの適用

既存システムが存在する場合、「ストラングラーフィグパターン」を使用して段階的に機能をマイクロサービスに移行します。

```mermaid
graph TB
    subgraph "Phase 1"
        M1[既存モノリス] --- A1[API Gateway]
        A1 --> E1[既存機能]
    end
    
    subgraph "Phase 2"
        M2[既存モノリス] --- A2[API Gateway]
        A2 --> E2[既存機能]
        A2 --> N1[新マイクロサービス: ユーザー管理]
    end
    
    subgraph "Phase 3"
        M3[既存モノリス] --- A3[API Gateway]
        A3 --> E3[既存機能の一部]
        A3 --> N2[マイクロサービス: ユーザー管理]
        A3 --> N3[マイクロサービス: 在庫管理]
        A3 --> N4[マイクロサービス: 注文管理]
    end
    
    subgraph "Phase 4"
        A4[API Gateway]
        A4 --> N5[マイクロサービス: ユーザー管理]
        A4 --> N6[マイクロサービス: 在庫管理]
        A4 --> N7[マイクロサービス: 注文管理]
        A4 --> N8[マイクロサービス: 支払い]
        A4 --> N9[マイクロサービス: クーポン]
        A4 --> N10[マイクロサービス: ポイント]
    end
    
    Phase1 --> Phase2
    Phase2 --> Phase3
    Phase3 --> Phase4
```

### データ移行戦略

1. **データ分析と設計**:
   - 既存データのスキーマと品質の分析
   - 新システムのデータモデル設計
   - マッピングルールの定義

2. **段階的データ移行**:
   - 初期ロード: 基本的なマスターデータの移行
   - 増分ロード: トランザクションデータの段階的移行
   - 双方向同期: 移行期間中の一時的なデータ同期メカニズム

3. **データ整合性確保**:
   - チェックサム検証
   - サンプリング監査
   - データ検証レポート

4. **フォールバック計画**:
   - ポイントインタイムリカバリー
   - ロールバックシナリオのテスト
   - 緊急時の手続きの確立

### 移行検証戦略

1. **機能同等性検証**:
   - 既存システムと新システムの並行実行
   - 同一インプットに対する出力比較
   - 自動化された比較テスト

2. **パフォーマンステスト**:
   - 負荷テスト（想定ピークの2倍の負荷）
   - 持続可能性テスト（24時間連続運用）
   - スケーラビリティテスト

3. **カナリアデプロイメント**:
   - トラフィックの段階的シフト（5% → 20% → 50% → 100%）
   - リアルタイムモニタリングと自動ロールバック
   - ユーザーフィードバックの収集

## リスク管理

### 主要リスク一覧

| リスク | 影響度 | 発生確率 | 緩和策 |
|-------|-------|---------|-------|
| マイクロサービス間の連携複雑化 | 高 | 中 | サービスメッシュの採用、API設計の標準化、契約テスト |
| パフォーマンスボトルネック | 高 | 中 | 早期からのパフォーマンステスト、スケーラビリティを考慮した設計、キャッシング戦略 |
| データ一貫性の課題 | 高 | 高 | イベントソーシングパターン、最終的一貫性モデル、冪等性の確保 |
| セキュリティ脆弱性 | 高 | 低 | セキュリティファーストの設計、継続的な脆弱性スキャン、定期的なペネトレーションテスト |
| 運用複雑性の増大 | 中 | 高 | 自動化の徹底、包括的な監視・ロギング、自己修復メカニズム |
| スキルセットのギャップ | 中 | 中 | チームトレーニング、知識共有セッション、外部専門家の活用 |
| 依存サービスの障害 | 中 | 低 | サーキットブレーカー、フォールバックメカニズム、冗長性 |
| コスト超過 | 中 | 中 | 継続的なコスト監視、リソース最適化、段階的なスケーリング |

### 技術的リスクと緩和策

#### 1. 分散トランザクション管理の複雑さ

- **リスク**: マイクロサービス間でのトランザクション整合性確保の難しさ
- **緩和策**:  
  - Sagaパターンの採用: 分散トランザクションを一連の補償可能なローカルトランザクションに分解
  - イベントソーシング: 状態変更イベントの記録による一貫性の確保
  - 冪等操作: 同じリクエストが複数回実行されても安全なAPI設計

- **実装例（Sagaパターン）**:

  ```java
  // 注文作成Saga
  public class OrderCreationSaga {
    @Autowired private CommandGateway commandGateway;
    @Autowired private QueryGateway queryGateway;
    
    public CompletableFuture<OrderResult> createOrder(CreateOrderCommand command) {
      return commandGateway.send(new ReserveInventoryCommand(command.getItems()))
        .thenCompose(result -> commandGateway.send(new ProcessPaymentCommand(command.getPaymentDetails())))
        .thenCompose(result -> commandGateway.send(new FinalizeOrderCommand(command.getOrderId())))
        .exceptionally(ex -> {
          // 補償トランザクション
          commandGateway.send(new CancelOrderCommand(command.getOrderId()));
          return new OrderResult(OrderStatus.FAILED, ex.getMessage());
        });
    }
  }
  ```

#### 2. サービス間通信の信頼性

- **リスク**: ネットワーク障害によるサービス連携の失敗
- **緩和策**:
  - 非同期通信: Kafkaなどを使用したイベント駆動アーキテクチャ
  - リトライメカニズム: 指数バックオフと最大リトライ回数の設定
  - サーキットブレーカー: 障害の連鎖的波及防止（Resilience4j, Spring Cloud Circuit Breaker）

- **実装例（サーキットブレーカー）**:

  ```java
  @Service
  public class InventoryServiceClient {
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public Mono<InventoryResponse> getInventory(String productId) {
      return webClient.get()
        .uri("/inventory/{productId}", productId)
        .retrieve()
        .bodyToMono(InventoryResponse.class);
    }
    
    public Mono<InventoryResponse> getInventoryFallback(String productId, Exception ex) {
      // Fallback processing (retrieve from cache or estimated value)
      return cacheService.getCachedInventory(productId)
        .switchIfEmpty(Mono.just(new InventoryResponse(productId, 0, false)));
    }
  }
  ```

#### 3. Data Consistency and Synchronization

- **Risk**: Data inconsistency between multiple data stores
- **Mitigation Strategies**:
  - Event-driven architecture: Process data changes through unified event streams
  - Eventual consistency model: Allow asynchronous data synchronization
  - Change Data Capture (CDC): Stream database changes (Debezium)

- **Implementation Example (CDC)**:

  ```yaml
  # Debezium Connector Configuration
  {
    "name": "inventory-connector",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "postgres",
      "database.password": "postgres",
      "database.dbname": "inventory",
      "database.server.name": "inventory-db",
      "table.include.list": "public.products,public.inventory",
      "topic.prefix": "inventory",
      "transforms": "unwrap",
      "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState"
    }
  }
  ```

### Operational Risks and Mitigation Strategies

#### 1. Complex Monitoring and Fault Diagnosis

- **Risk**: Difficulty in problem identification in distributed systems
- **Mitigation Strategies**:
  - Distributed tracing: Visualize request flows (Jaeger, Zipkin)
  - Centralized log management: Unified log analysis (ELK Stack, Azure Log Analytics)
  - Correlation IDs: Consistent request tracking across services

- **Implementation Example (Distributed Tracing)**:

  ```java
  @RestController
  public class ProductController {
    @Autowired private ProductService productService;
    @Autowired private Tracer tracer;
    
    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable String id, @RequestHeader HttpHeaders headers) {
      Span span = tracer.buildSpan("getProduct").start();
      try (Scope scope = tracer.scopeManager().activate(span)) {
        span.setTag("product.id", id);
        // Extract correlation ID from request headers
        String correlationId = headers.getFirst("X-Correlation-ID");
        span.setTag("correlation.id", correlationId);
        
        Product product = productService.findById(id);
        if (product == null) {
          span.setTag("error", true);
          span.log(Map.of("event", "product_not_found", "product.id", id));
          return ResponseEntity.notFound().build();
        }
        
        span.log("product_retrieved_successfully");
        return ResponseEntity.ok(product);
      } finally {
        span.finish();
      }
    }
  }
  ```

#### 2. Deployment Complexity

- **Risk**: Difficulty in coordinated deployment of multiple services
- **Mitigation Strategies**:
  - CI/CD automation: Complete pipeline automation (GitHub Actions, Azure DevOps)
  - Canary releases: Gradual traffic migration
  - Blue/green deployment: Zero-downtime deployment

- **Implementation Example (GitHub Actions CI/CD)**:

  ```yaml
  name: CI/CD Pipeline

  on:
    push:
      branches: [ main ]
    pull_request:
      branches: [ main ]

  jobs:
    build:
      runs-on: ubuntu-latest
      steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build with Maven
        run: mvn -B package --file pom.xml
      - name: Run Tests
        run: mvn test
      - name: Build and Push Docker Image
        uses: docker/build-push-action@v2
        with:
          context: .
          push: true
          tags: ${{ secrets.ACR_LOGIN_SERVER }}/inventory-service:${{ github.sha }}
          
    deploy:
      needs: build
      runs-on: ubuntu-latest
      steps:
      - name: Azure Login
        uses: azure/login@v1
        with:
          creds: ${{ secrets.AZURE_CREDENTIALS }}
      - name: Deploy to AKS
        uses: azure/k8s-deploy@v1
        with:
          namespace: ski-shop
          manifests: |
            kubernetes/deployment.yaml
            kubernetes/service.yaml
          images: |
            ${{ secrets.ACR_LOGIN_SERVER }}/inventory-service:${{ github.sha }}
          strategy: canary
          percentage: 20
  ```

### Business Risks and Mitigation Strategies

#### 1. Increased Development Cost and Time

- **Risk**: Increased initial development costs for microservices
- **Mitigation Strategies**:
  - Gradual migration: Prioritize implementation based on business value
  - Shared libraries: Common code for cross-cutting concerns
  - Improved developer experience: Internal developer portal, self-service development environments

#### 2. Skill Set and Team Composition

- **Risk**: Learning curve for new technology adoption
- **Mitigation Strategies**:
  - Training programs: Regular technical study sessions
  - Pair programming: Promote knowledge sharing
  - Clear architectural guidelines: Document design principles and patterns

#### 3. Long-term Maintainability

- **Risk**: Maintenance complexity due to diverse technology stacks
- **Mitigation Strategies**:
  - Technology stack standardization: Limit language and framework choices
  - Internal developer portal: Service catalog and documentation
  - Comprehensive documentation: Maintain Architecture Decision Records (ADR)

### Incident Response Plan

1. **Incident Detection**:
   - Automated alerts (availability, latency, error rates)
   - Anomaly detection (machine learning-based)
   - User reporting channels

2. **Initial Response**:
   - Form response team (incident manager, technical lead, communications)
   - Identify and classify impact scope (P1-P4)
   - Implement initial mitigation measures

3. **Escalation Process**:
   - Clear escalation paths
   - Pre-defined response timelines
   - Senior management notification criteria

4. **Recovery Process**:
   - Pre-defined recovery procedures
   - Change management and approval processes
   - Rollback decision criteria

5. **Post-incident Analysis**:
   - Root cause analysis (RCA)
   - Identify and implement prevention measures
   - Update knowledge base
