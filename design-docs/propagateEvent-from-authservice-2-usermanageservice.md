# Event Propagation Design from Authentication Service to User Management Service

## Overview

This document details the design for achieving robust and loosely coupled event propagation between the `authentication-service` and `user-management-service`. It focuses on user registration and deletion events using the Saga pattern. The design covers implementations for both local development environment (Redis Streams + REST API) and production environment (Azure Service Bus + Azure Event Grid).

## System Architecture

### Architecture Diagram

```ascii
+------------------+        Events        +----------------------+
|                  |     ------------>    |                      |
| Authentication   |                      |  User Management     |
| Service          |     <------------    |  Service             |
+------------------+   Status Updates     +----------------------+
        |                                           |
        | Event Publishing                          | Event Subscription/Processing
        |                                           |
        v                                           v
+------------------+                      +----------------------+
|                  |                      |                      |
| Message Broker   |                      |  Message Broker      |
|                  |                      |                      |
+------------------+                      +----------------------+
 Local: Redis Streams                      Local: Redis Consumer
 Production: Azure Service Bus             Production: Azure Service Bus
```

### Design Principles

1. **Loose Coupling**: Authentication Service and User Management Service operate independently and communicate only through events.
2. **Resilience**: If one service is temporarily unavailable, the other service can continue to function.
3. **Consistency**: The Saga pattern ensures eventual consistency of distributed operations.
4. **Observability**: Comprehensive metrics and monitoring for all events and operations.
5. **Idempotency**: Operations are idempotent to prevent duplicate processing.

## Actual Implementation Architecture

### Key Components

#### Authentication Service Components

- **EventPublishingService**: Publishes user registration and deletion events
- **UserRegistrationService**: Handles user registration logic and triggers events
- **SagaState Entity**: Tracks saga transaction states
- **RedisTemplate**: For local development event publishing
- **AzureServiceBusEventPublisher**: For production environment (optional)

#### User Management Service Components

- **EventConsumerService**: Consumes events from Redis/Azure Service Bus
- **EventHandlerService**: Handles event processing with transaction management
- **SagaOrchestrator**: Orchestrates saga workflows for user operations
- **UserEventService**: Business logic for user profile management
- **StatusFeedbackPublishingService**: Sends status updates back to Authentication Service

### Event Flow Implementation

1. **User Registration Flow**:
   - User registers via Authentication Service API
   - `UserRegistrationService.registerUser()` creates user account
   - `EventPublishingService.publishUserRegisteredEvent()` publishes event to Redis/Azure Service Bus
   - `EventConsumerService.onMessage()` receives event in User Management Service
   - `SagaOrchestrator.startUserRegistrationSaga()` processes the event
   - `UserEventService.createUserProfile()` creates user profile
   - Status feedback sent back to Authentication Service

2. **Event Publishing Mechanism**:
   - Events published to Redis channels for local development
   - Azure Service Bus used for production environment
   - Retry mechanism with exponential backoff
   - Saga state tracking for transaction management

## Event Schemas (Actual Implementation)

### User Registration Event

```json
{
  "eventId": "uuid-string",
  "eventType": "USER_REGISTERED",
  "timestamp": "ISO-8601-timestamp",
  "version": "1.0",
  "producer": "authentication-service",
  "payload": {
    "userId": "uuid-string",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "+1234567890",
    "status": "PENDING_VERIFICATION",
    "createdAt": "ISO-8601-timestamp",
    "additionalAttributes": {
      "username": "johndoe"
    }
  },
  "correlationId": "uuid-string",
  "sagaId": "uuid-string",
  "retry": 0
}
```

### User Deletion Event

```json
{
  "eventId": "uuid-string",
  "eventType": "USER_DELETED",
  "timestamp": "ISO-8601-timestamp",
  "version": "1.0",
  "producer": "authentication-service",
  "payload": {
    "userId": "uuid-string",
    "reason": "User requested account deletion",
    "deletedAt": "ISO-8601-timestamp"
  },
  "correlationId": "uuid-string",
  "sagaId": "uuid-string",
  "retry": 0
}
```

### Status Feedback Event

```json
{
  "eventId": "uuid-string",
  "eventType": "USER_MANAGEMENT_STATUS",
  "timestamp": "ISO-8601-timestamp",
  "version": "1.0",
  "producer": "user-management-service",
  "payload": {
    "sagaId": "uuid-string",
    "status": "SUCCESS",
    "processingStatus": "PROFILE_CREATED",
    "message": "User profile created successfully",
    "userId": "uuid-string"
  },
  "correlationId": "uuid-string",
  "sagaId": "uuid-string",
  "retry": 0
}
```

## Saga Pattern Implementation

### Overview of the Saga Pattern

The Saga pattern is a microservices design pattern that manages distributed transactions across multiple services. Unlike traditional ACID transactions that can span multiple databases, the Saga pattern ensures data consistency through a series of local transactions, where each step can be compensated if a later step fails.

#### Key Characteristics of Saga Pattern

1. **Sequence of Local Transactions**: Each step in the saga is a local transaction that commits immediately
2. **Compensating Actions**: For each transaction, there's a corresponding compensating transaction that can undo its effects
3. **Eventual Consistency**: The system achieves consistency over time rather than immediately
4. **Fault Tolerance**: The pattern handles failures gracefully through compensation
5. **Loose Coupling**: Services remain independent and communicate through events

#### Types of Saga Implementation

Our system implements the **Orchestration-based Saga** pattern, where a central coordinator (SagaOrchestrator) manages the workflow:

- **Orchestrator**: Controls the saga flow and decides the next step
- **Participants**: Services that execute business logic (Authentication Service, User Management Service)
- **State Management**: Tracks saga progress and handles failures

### Saga Implementation in Our System

#### Saga Workflow Components

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Saga Orchestration Flow                          │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│                  │    │                  │    │                  │
│ Authentication   │    │   Message        │    │ User Management  │
│ Service          │    │   Broker         │    │ Service          │
│                  │    │                  │    │                  │
│ - UserRegService │    │ - Redis/Azure    │    │ - EventConsumer  │
│ - EventPublisher │    │   Service Bus    │    │ - SagaOrchestrator│
│ - SagaState      │    │                  │    │ - UserEventService│
│                  │    │                  │    │                  │
└──────────────────┘    └──────────────────┘    └──────────────────┘
         │                        │                        │
         │ 1. Publish Event       │                        │
         ├───────────────────────►│                        │
         │                        │ 2. Consume Event       │
         │                        ├───────────────────────►│
         │                        │                        │
         │                        │ 3. Status Feedback     │
         │                        │◄───────────────────────┤
         │ 4. Receive Status      │                        │
         │◄───────────────────────┤                        │
```

#### Detailed User Registration Saga Flow

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                    User Registration Saga Sequence                         │
└─────────────────────────────────────────────────────────────────────────────┘

Client  Auth Service    Message Broker   User Mgmt Service   Database
  │         │                │                │               │
  │ 1. POST │                │                │               │
  │ /register               │                │               │
  ├────────►│                │                │               │
  │         │ 2. Create User │                │               │
  │         │   Account      │                │               │
  │         ├───────────────────────────────────────────────►│
  │         │                │                │               │
  │         │◄───────────────────────────────────────────────┤
  │         │ 3. Save Saga   │                │               │
  │         │   State        │                │               │
  │         ├───────────────────────────────────────────────►│
  │         │                │                │               │
  │         │ 4. Publish     │                │               │
  │         │   USER_REGISTERED              │               │
  │         ├───────────────►│                │               │
  │         │                │ 5. Forward     │               │
  │         │                │   Event        │               │
  │         │                ├───────────────►│               │
  │         │                │                │ 6. Start Saga│
  │         │                ├──────────────►│               │
  │         │                │                │               │
  │         │                │                │ 7. Create     │
  │         │                │                │   Profile     │
  │         │                ├──────────────►│               │
  │         │                │                │               │
  │         │                │                │◄──────────────┤
  │         │                │ 8. Publish     │               │
  │         │                │   Status       │               │
  │         │                │◄───────────────┤               │
  │         │ 9. Receive     │                │               │
  │         │   Status       │                │               │
  │         │◄───────────────┤                │               │
  │         │ 10. Update     │                │               │
  │         │    Saga State  │                │               │
  │         ├───────────────────────────────────────────────►│
  │ 11. Response            │                │               │
  │◄────────┤                │                │               │
```

#### Saga State Machine

Our implementation uses a state machine to track saga progress:

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Saga State Transitions                             │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────┐
                    │   SAGA_STARTED  │
                    └─────────┬───────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │SAGA_IN_PROGRESS │
                    └─────────┬───────┘
                              │
                ┌─────────────┼─────────────┐
                │             │             │
                ▼             ▼             ▼
    ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
    │ SAGA_COMPLETED  │ │  SAGA_FAILED    │ │ SAGA_COMPENSATING│
    └─────────────────┘ └─────────────────┘ └─────────┬───────┘
                                                      │
                                                      ▼
                                            ┌─────────────────┐
                                            │SAGA_COMPENSATED │
                                            └─────────────────┘
```

#### Error Handling and Compensation

When a saga step fails, the system implements compensation logic:

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Compensation Flow Example                           │
└─────────────────────────────────────────────────────────────────────────────┘

Normal Flow:
Step 1: Create User Account ✓
Step 2: Publish Event ✓
Step 3: Create User Profile ✗ (FAILS)

Compensation Flow:
Step 3: Profile Creation Failed
Step 2: [No compensation needed - event already sent]
Step 1: Mark User Account as FAILED/ROLLBACK
```

### Class Diagram and Implementation Details

#### Authentication Service Classes

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Authentication Service Architecture                     │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                          UserRegistrationService                            │
├──────────────────────────────────────────────────────────────────────────────┤
│ - userRepository: UserRepository                                             │
│ - eventPublishingService: EventPublishingService                             │
│ - sagaStateRepository: SagaStateRepository                                   │
├──────────────────────────────────────────────────────────────────────────────┤
│ + registerUser(registrationRequest: UserRegistrationRequest): User           │
│ + deleteUser(userId: UUID): void                                             │
│ - createUserAccount(request: UserRegistrationRequest): User                  │
│ - initializeSaga(user: User, eventType: String): SagaState                   │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ uses
┌──────────────────────────────────────────────────────────────────────────────┐
│                          EventPublishingService                             │
├──────────────────────────────────────────────────────────────────────────────┤
│ - redisTemplate: RedisTemplate<String, Object>                              │
│ - objectMapper: ObjectMapper                                                 │
│ - retryTemplate: RetryTemplate                                               │
├──────────────────────────────────────────────────────────────────────────────┤
│ + publishUserRegisteredEvent(user: User, sagaId: String): void               │
│ + publishUserDeletedEvent(userId: UUID, sagaId: String): void                │
│ - publishEventToRedis(event: Object, channel: String): void                  │
│ - buildUserRegisteredEvent(user: User, sagaId: String): UserRegisteredEvent  │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ manages
┌──────────────────────────────────────────────────────────────────────────────┐
│                              SagaState                                      │
├──────────────────────────────────────────────────────────────────────────────┤
│ - id: UUID                                                                   │
│ - sagaId: String                                                             │
│ - eventType: String                                                          │
│ - sagaType: String                                                           │
│ - userId: UUID                                                               │
│ - status: String                                                             │
│ - sagaStatus: SagaStatus                                                     │
│ - correlationId: String                                                      │
│ - startTime: LocalDateTime                                                   │
│ - endTime: LocalDateTime                                                     │
│ - retryCount: Integer                                                        │
│ - errorMessage: String                                                       │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### User Management Service Classes

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                    User Management Service Architecture                     │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                          EventConsumerService                               │
├──────────────────────────────────────────────────────────────────────────────┤
│ - eventHandlerService: EventHandlerService                                   │
│ - objectMapper: ObjectMapper                                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│ + onMessage(message: String, channel: String): void                          │
│ + handleUserRegisteredEvent(event: UserRegisteredEvent): void                │
│ + handleUserDeletedEvent(event: UserDeletedEvent): void                      │
│ - parseEvent(message: String): Object                                        │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ delegates to
┌──────────────────────────────────────────────────────────────────────────────┐
│                           EventHandlerService                               │
├──────────────────────────────────────────────────────────────────────────────┤
│ - sagaOrchestrator: SagaOrchestrator                                         │
│ - transactionManager: PlatformTransactionManager                             │
├──────────────────────────────────────────────────────────────────────────────┤
│ + handleUserRegisteredEvent(event: UserRegisteredEvent): void                │
│ + handleUserDeletedEvent(event: UserDeletedEvent): void                      │
│ - processEventWithTransaction(event: Object): void                           │
│ - handleEventProcessingError(error: Exception, event: Object): void          │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ coordinates
┌──────────────────────────────────────────────────────────────────────────────┐
│                            SagaOrchestrator                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│ - userEventService: UserEventService                                         │
│ - statusFeedbackService: StatusFeedbackPublishingService                     │
│ - sagaTransactionRepository: SagaTransactionRepository                       │
│ - processedEventRepository: ProcessedEventRepository                         │
├──────────────────────────────────────────────────────────────────────────────┤
│ + startUserRegistrationSaga(event: UserRegisteredEvent): void                │
│ + startUserDeletionSaga(event: UserDeletedEvent): void                       │
│ + compensateUserRegistration(sagaId: String): void                           │
│ + compensateUserDeletion(sagaId: String): void                               │
│ - validateEventData(event: Object): boolean                                  │
│ - isEventAlreadyProcessed(sagaId: String): boolean                           │
│ - createSagaTransaction(event: Object): SagaTransaction                      │
│ - updateSagaStatus(sagaId: String, status: String): void                     │
│ - sendStatusFeedback(sagaId: String, status: String, message: String): void  │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ executes business logic
┌──────────────────────────────────────────────────────────────────────────────┐
│                            UserEventService                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│ - userRepository: UserRepository                                             │
│ - userMapper: UserMapper                                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│ + createUserProfile(event: UserRegisteredEvent): User                        │
│ + deleteUserProfile(userId: UUID): void                                      │
│ + updateUserProfile(userId: UUID, updateData: Object): User                  │
│ - validateUserData(userData: Object): boolean                                │
│ - checkForDuplicateUser(email: String): boolean                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Key Implementation Classes Explained

#### 1. UserRegistrationService (Authentication Service)

**Purpose**: Orchestrates user registration in the authentication service and initiates saga transactions.

**Key Responsibilities**:

- Creates user accounts in the authentication database
- Initializes saga state tracking
- Triggers event publishing for user registration
- Handles user deletion requests

**Implementation Details**:

```java
@Service
@Transactional
public class UserRegistrationService {
    
    public User registerUser(UserRegistrationRequest request) {
        // Step 1: Create user account locally
        User user = createUserAccount(request);
        
        // Step 2: Initialize saga state
        SagaState sagaState = initializeSaga(user, "USER_REGISTRATION");
        
        // Step 3: Publish event to trigger downstream processing
        eventPublishingService.publishUserRegisteredEvent(user, sagaState.getSagaId());
        
        return user;
    }
    
    private SagaState initializeSaga(User user, String eventType) {
        SagaState sagaState = SagaState.builder()
            .sagaId(UUID.randomUUID().toString())
            .eventType(eventType)
            .userId(user.getId())
            .status("ACCOUNT_CREATED")
            .sagaStatus(SagaStatus.SAGA_STARTED)
            .startTime(LocalDateTime.now())
            .build();
        
        return sagaStateRepository.save(sagaState);
    }
}
```

#### 2. EventPublishingService (Authentication Service)

**Purpose**: Publishes events to the message broker with retry mechanisms and error handling.

**Key Responsibilities**:

- Formats events according to the defined schema
- Publishes events to Redis or Azure Service Bus
- Implements retry logic with exponential backoff
- Tracks event publishing status

**Implementation Details**:

```java
@Service
public class EventPublishingService {
    
    @Retryable(value = {Exception.class}, maxAttempts = 3)
    public void publishUserRegisteredEvent(User user, String sagaId) {
        try {
            UserRegisteredEvent event = buildUserRegisteredEvent(user, sagaId);
            publishEventToRedis(event, "skishop-local:events:user_registered");
            
            // Update saga state to indicate successful publishing
            updateSagaStatus(sagaId, "EVENT_PUBLISHED");
            
        } catch (Exception e) {
            updateSagaStatus(sagaId, "EVENT_PUBLISH_FAILED");
            throw new EventPublishingException("Failed to publish user registered event", e);
        }
    }
    
    private void publishEventToRedis(Object event, String channel) {
        String eventJson = objectMapper.writeValueAsString(event);
        redisTemplate.convertAndSend(channel, eventJson);
    }
}
```

#### 3. SagaOrchestrator (User Management Service)

**Purpose**: Central coordinator that manages the saga workflow in the user management service.

**Key Responsibilities**:

- Validates incoming events
- Prevents duplicate processing
- Orchestrates business logic execution
- Manages compensation logic for failures
- Sends status feedback to the authentication service

**Implementation Details**:

```java
@Service
@Transactional
public class SagaOrchestrator {
    
    public void startUserRegistrationSaga(UserRegisteredEvent event) {
        String sagaId = event.getSagaId();
        
        try {
            // Step 1: Validate event data
            if (!validateEventData(event)) {
                throw new InvalidEventDataException("Invalid event data received");
            }
            
            // Step 2: Check for duplicate processing
            if (isEventAlreadyProcessed(sagaId)) {
                log.info("Event {} already processed, skipping", sagaId);
                return;
            }
            
            // Step 3: Create saga transaction record
            SagaTransaction sagaTransaction = createSagaTransaction(event);
            
            // Step 4: Execute business logic
            User userProfile = userEventService.createUserProfile(event);
            
            // Step 5: Update saga status
            updateSagaStatus(sagaId, "PROFILE_CREATED");
            
            // Step 6: Send success feedback
            sendStatusFeedback(sagaId, "SUCCESS", "User profile created successfully");
            
        } catch (Exception e) {
            // Compensation logic
            compensateUserRegistration(sagaId);
            sendStatusFeedback(sagaId, "FAILED", e.getMessage());
            throw e;
        }
    }
    
    public void compensateUserRegistration(String sagaId) {
        try {
            // Find the saga transaction
            SagaTransaction sagaTransaction = sagaTransactionRepository.findBySagaId(sagaId);
            
            if (sagaTransaction != null && "PROFILE_CREATED".equals(sagaTransaction.getProcessingStatus())) {
                // If user profile was created, delete it
                UUID userId = UUID.fromString(sagaTransaction.getEntityId());
                userEventService.deleteUserProfile(userId);
                
                updateSagaStatus(sagaId, "COMPENSATED");
                log.info("Successfully compensated user registration for saga {}", sagaId);
            }
        } catch (Exception e) {
            log.error("Failed to compensate user registration for saga {}", sagaId, e);
            updateSagaStatus(sagaId, "COMPENSATION_FAILED");
        }
    }
}
```

#### 4. UserEventService (User Management Service)

**Purpose**: Executes the core business logic for user profile management.

**Key Responsibilities**:

- Creates user profiles in the user management database
- Validates user data integrity
- Checks for duplicate users
- Handles user profile updates and deletions

**Implementation Details**:

```java
@Service
@Transactional
public class UserEventService {
    
    public User createUserProfile(UserRegisteredEvent event) {
        try {
            // Step 1: Validate user data
            if (!validateUserData(event.getPayload())) {
                throw new InvalidUserDataException("Invalid user data in event");
            }
            
            // Step 2: Check for duplicate user
            if (checkForDuplicateUser(event.getPayload().getEmail())) {
                throw new DuplicateUserException("User with email already exists");
            }
            
            // Step 3: Create user profile
            User user = User.builder()
                .id(event.getPayload().getUserId())
                .email(event.getPayload().getEmail())
                .firstName(event.getPayload().getFirstName())
                .lastName(event.getPayload().getLastName())
                .phoneNumber(event.getPayload().getPhoneNumber())
                .status("ACTIVE")
                .emailVerified(false)
                .createdAt(LocalDateTime.now())
                .build();
            
            return userRepository.save(user);
            
        } catch (Exception e) {
            log.error("Failed to create user profile for user {}", 
                     event.getPayload().getUserId(), e);
            throw new UserProfileCreationException("Failed to create user profile", e);
        }
    }
    
    private boolean checkForDuplicateUser(String email) {
        return userRepository.existsByEmail(email);
    }
    
    private boolean validateUserData(UserRegisteredEvent.Payload payload) {
        return payload != null 
            && payload.getUserId() != null 
            && payload.getEmail() != null 
            && !payload.getEmail().trim().isEmpty()
            && payload.getFirstName() != null 
            && !payload.getFirstName().trim().isEmpty();
    }
}
```

### Saga Transaction States and Lifecycle

#### Saga Transaction Entity

The `SagaTransaction` entity tracks the complete lifecycle of a saga:

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SagaTransaction Lifecycle                          │
└─────────────────────────────────────────────────────────────────────────────┘

Creation → Validation → Processing → Completion/Failure → Cleanup

1. STARTED           │ Saga transaction created and initialized
2. VALIDATING        │ Event data validation in progress
3. VALIDATED         │ Event data validation successful
4. PROCESSING        │ Business logic execution in progress  
5. COMPLETED         │ All steps completed successfully
6. FAILED            │ Business logic execution failed
7. COMPENSATING      │ Compensation logic in progress
8. COMPENSATED       │ Compensation completed successfully
9. COMPENSATION_FAILED│ Compensation logic failed
```

#### State Persistence Strategy

**SagaTransaction Table Structure**:

```sql
CREATE TABLE saga_transactions (
    id UUID PRIMARY KEY,
    saga_id VARCHAR(255) UNIQUE NOT NULL,
    correlation_id VARCHAR(255),
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    processing_status VARCHAR(100),
    entity_id VARCHAR(255),          -- User ID being processed
    context_data JSONB,              -- Additional saga context
    steps_completed TEXT[],          -- Array of completed steps
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    timeout_at TIMESTAMP,            -- Saga timeout
    retry_count INTEGER DEFAULT 0
);
```

This comprehensive implementation ensures robust distributed transaction management with proper error handling, compensation logic, and observability throughout the saga lifecycle.

### Saga Pattern Benefits and Trade-offs

#### Benefits of Using Saga Pattern

1. **Fault Tolerance**: Each service can fail independently without affecting others
2. **Scalability**: Services can scale independently based on their load
3. **Autonomy**: Services maintain their own data and business logic
4. **Flexibility**: Easy to add new services or modify existing workflows
5. **Eventual Consistency**: Achieves consistency across distributed systems over time
6. **Performance**: Avoids blocking long-running distributed transactions

#### Trade-offs and Considerations

1. **Complexity**: More complex than monolithic transactions
2. **Eventual Consistency**: Data may be temporarily inconsistent
3. **Debugging**: Distributed traces can be harder to debug
4. **Compensation Logic**: Requires careful design of compensating actions
5. **State Management**: Need to track saga state across services
6. **Monitoring**: Requires comprehensive monitoring and alerting

#### Comparison with Other Patterns

| Pattern | Consistency | Complexity | Performance | Use Case |
|---------|-------------|------------|-------------|----------|
| **2PC (Two-Phase Commit)** | Strong | Medium | Low | Small, tightly-coupled systems |
| **Saga Pattern** | Eventual | High | High | Large, distributed microservices |
| **Event Sourcing** | Eventual | Very High | High | Systems requiring full audit trails |
| **Synchronous API** | Strong | Low | Medium | Simple, real-time operations |

### Performance Considerations

#### Latency Optimization

1. **Asynchronous Processing**: Events are processed asynchronously to reduce response times
2. **Parallel Processing**: Independent saga steps can be executed in parallel
3. **Caching**: Redis caching for frequently accessed saga states
4. **Connection Pooling**: Database connection pooling to reduce overhead

#### Throughput Optimization

1. **Batch Processing**: Group multiple events for batch processing when possible
2. **Message Buffering**: Use message broker buffering for high-throughput scenarios
3. **Horizontal Scaling**: Scale saga orchestrators horizontally as needed
4. **Resource Monitoring**: Monitor CPU, memory, and I/O usage for optimization

#### Example Performance Metrics

```ascii
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Saga Performance Metrics                           │
└─────────────────────────────────────────────────────────────────────────────┘

Typical User Registration Saga Timing:
├── Event Publishing: 50-100ms
├── Message Broker Transit: 10-50ms  
├── Event Processing: 100-200ms
├── Database Operations: 50-150ms
└── Status Feedback: 50-100ms
─────────────────────────────────
Total End-to-End: 260-600ms

Peak Throughput (with horizontal scaling):
├── Authentication Service: 1000 registrations/minute
├── User Management Service: 1200 events/minute
├── Message Broker: 5000 messages/minute
└── Database: 800 transactions/minute
```

### Saga Pattern Best Practices

#### Design Best Practices

1. **Idempotent Operations**: Ensure all saga steps can be safely retried
2. **Compensation Design**: Design compensation logic for each step
3. **Timeout Handling**: Implement timeouts for long-running operations
4. **State Validation**: Validate saga state at each step
5. **Event Ordering**: Handle out-of-order events gracefully

#### Implementation Best Practices

1. **Error Classification**: Distinguish between transient and permanent errors
2. **Retry Strategies**: Use exponential backoff for retries
3. **Circuit Breakers**: Implement circuit breakers for external dependencies
4. **Monitoring**: Add comprehensive logging and metrics
5. **Testing**: Create thorough integration tests for saga flows

#### Common Pitfalls to Avoid

1. **Missing Compensation**: Not implementing compensation for all steps
2. **Long-Running Sagas**: Creating sagas that run too long without checkpoints
3. **Complex Dependencies**: Creating circular or overly complex dependencies
4. **Insufficient Monitoring**: Not monitoring saga health and performance
5. **Resource Leaks**: Not cleaning up resources after saga completion

### Advanced Saga Patterns

#### Saga Timeout Management

```java
@Component
public class SagaTimeoutManager {
    
    @Scheduled(fixedDelay = 60000) // Check every minute
    public void checkSagaTimeouts() {
        List<SagaTransaction> timeoutSagas = sagaRepository
            .findByStatusAndTimeoutAtBefore("IN_PROGRESS", LocalDateTime.now());
            
        timeoutSagas.forEach(saga -> {
            log.warn("Saga {} timed out, initiating compensation", saga.getSagaId());
            sagaOrchestrator.compensate(saga.getSagaId());
        });
    }
}
```

#### Saga Recovery Patterns

```java
@Component
public class SagaRecoveryService {
    
    public void recoverFailedSagas() {
        // Find sagas in inconsistent state
        List<SagaTransaction> failedSagas = sagaRepository
            .findByStatusIn(Arrays.asList("FAILED", "COMPENSATION_FAILED"));
            
        failedSagas.forEach(saga -> {
            if (isRecoverable(saga)) {
                retrySaga(saga);
            } else {
                markAsManualIntervention(saga);
            }
        });
    }
    
    private boolean isRecoverable(SagaTransaction saga) {
        return saga.getRetryCount() < MAX_RETRY_ATTEMPTS
            && Duration.between(saga.getStartedAt(), LocalDateTime.now()).toHours() < 24;
    }
}
```

#### Event Deduplication Strategy

```java
@Component
public class EventDeduplicationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final int DEDUP_TTL_HOURS = 24;
    
    public boolean isDuplicateEvent(String eventId) {
        String key = "event:processed:" + eventId;
        return redisTemplate.hasKey(key);
    }
    
    public void markEventProcessed(String eventId) {
        String key = "event:processed:" + eventId;
        redisTemplate.opsForValue().set(key, "processed", 
            Duration.ofHours(DEDUP_TTL_HOURS));
    }
}
```

### Saga Monitoring and Observability

#### Key Metrics to Monitor

1. **Saga Success Rate**: Percentage of sagas completing successfully
2. **Saga Duration**: Average time to complete saga transactions
3. **Compensation Rate**: Percentage of sagas requiring compensation
4. **Event Processing Latency**: Time between event publication and processing
5. **Error Rate by Step**: Error rates for individual saga steps
6. **Message Broker Health**: Queue depths, processing rates, and errors

#### Monitoring Implementation

```java
@Component
public class SagaMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter sagaStartCounter;
    private final Counter sagaCompletionCounter;
    private final Timer sagaDurationTimer;
    
    public SagaMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.sagaStartCounter = Counter.builder("saga.started")
            .description("Number of sagas started")
            .tag("type", "user_registration")
            .register(meterRegistry);
            
        this.sagaCompletionCounter = Counter.builder("saga.completed")
            .description("Number of sagas completed")
            .register(meterRegistry);
            
        this.sagaDurationTimer = Timer.builder("saga.duration")
            .description("Saga execution duration")
            .register(meterRegistry);
    }
    
    public void recordSagaStart(String sagaType) {
        sagaStartCounter.increment(Tags.of("saga_type", sagaType));
    }
    
    public void recordSagaCompletion(String sagaType, String status, Duration duration) {
        sagaCompletionCounter.increment(Tags.of("saga_type", sagaType, "status", status));
        sagaDurationTimer.record(duration);
    }
}
```

#### Prometheus Metrics Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'authentication-service'
    static_configs:
      - targets: ['auth-service:8080']
    metrics_path: '/actuator/prometheus'
    
  - job_name: 'user-management-service'
    static_configs:
      - targets: ['user-service:8081']
    metrics_path: '/actuator/prometheus'

rule_files:
  - "saga_alerts.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

#### Grafana Dashboard Configuration

```json
{
  "dashboard": {
    "title": "Saga Pattern Monitoring",
    "panels": [
      {
        "title": "Saga Success Rate",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(saga_completed_total{status=\"success\"}[5m]) / rate(saga_started_total[5m]) * 100"
          }
        ]
      },
      {
        "title": "Average Saga Duration",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(saga_duration_seconds_sum[5m]) / rate(saga_duration_seconds_count[5m])"
          }
        ]
      },
      {
        "title": "Saga Status Distribution",
        "type": "piechart",
        "targets": [
          {
            "expr": "increase(saga_completed_total[1h])"
          }
        ]
      }
    ]
  }
}
```

#### Alert Rules for Saga Monitoring

```yaml
# saga_alerts.yml
groups:
  - name: saga-alerts
    rules:
      - alert: HighSagaFailureRate
        expr: rate(saga_completed_total{status="failed"}[5m]) / rate(saga_started_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High saga failure rate detected"
          description: "Saga failure rate is {{ $value | humanizePercentage }}"
          
      - alert: SagaDurationTooHigh
        expr: rate(saga_duration_seconds_sum[5m]) / rate(saga_duration_seconds_count[5m]) > 30
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Saga duration exceeding threshold"
          description: "Average saga duration is {{ $value }}s"
          
      - alert: MessageBrokerDown
        expr: up{job="redis"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Message broker is down"
          description: "Redis/Message broker is not responding"
```

#### Distributed Tracing with Jaeger

```java
@Component
public class SagaTracingService {
    
    public void startSagaTrace(String sagaId, String sagaType) {
        Span span = tracer.nextSpan()
            .name("saga-execution")
            .tag("saga.id", sagaId)
            .tag("saga.type", sagaType)
            .start();
            
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Saga execution logic
        } finally {
            span.end();
        }
    }
    
    public void addSagaStepTrace(String stepName, String status) {
        Span currentSpan = tracer.nextSpan()
            .name("saga-step")
            .tag("step.name", stepName)
            .tag("step.status", status)
            .start();
            
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(currentSpan)) {
            // Step execution logic
        } finally {
            currentSpan.end();
        }
    }
}
```

### Saga Testing Strategies

#### Unit Testing Saga Components

```java
@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {
    
    @Mock
    private UserEventService userEventService;
    
    @Mock
    private StatusFeedbackPublishingService statusFeedbackService;
    
    @InjectMocks
    private SagaOrchestrator sagaOrchestrator;
    
    @Test
    @DisplayName("Should successfully complete user registration saga")
    void shouldCompleteUserRegistrationSaga() {
        // Given
        UserRegisteredEvent event = createTestUserRegisteredEvent();
        User expectedUser = createTestUser();
        when(userEventService.createUserProfile(event)).thenReturn(expectedUser);
        
        // When
        sagaOrchestrator.startUserRegistrationSaga(event);
        
        // Then
        verify(userEventService).createUserProfile(event);
        verify(statusFeedbackService).publishStatusUpdate(
            eq(event.getSagaId()), 
            eq("SUCCESS"), 
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should compensate when user profile creation fails")
    void shouldCompensateOnProfileCreationFailure() {
        // Given
        UserRegisteredEvent event = createTestUserRegisteredEvent();
        when(userEventService.createUserProfile(event))
            .thenThrow(new UserProfileCreationException("Database error"));
        
        // When & Then
        assertThrows(UserProfileCreationException.class, () -> {
            sagaOrchestrator.startUserRegistrationSaga(event);
        });
        
        verify(statusFeedbackService).publishStatusUpdate(
            eq(event.getSagaId()), 
            eq("FAILED"), 
            anyString()
        );
    }
}
```

#### Integration Testing with TestContainers

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:13:///test",
    "spring.redis.host=localhost",
    "spring.redis.port=6370"
})
class SagaIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6-alpine")
            .withExposedPorts(6379);
    
    @Autowired
    private UserRegistrationService userRegistrationService;
    
    @Autowired
    private SagaOrchestrator sagaOrchestrator;
    
    @Test
    @DisplayName("Should complete end-to-end user registration saga")
    void shouldCompleteEndToEndUserRegistrationSaga() throws InterruptedException {
        // Given
        UserRegistrationRequest request = createTestRegistrationRequest();
        
        // When
        User registeredUser = userRegistrationService.registerUser(request);
        
        // Wait for async processing
        Thread.sleep(2000);
        
        // Then
        assertThat(registeredUser).isNotNull();
        assertThat(registeredUser.getId()).isNotNull();
        
        // Verify saga state
        SagaState sagaState = sagaStateRepository.findByUserId(registeredUser.getId());
        assertThat(sagaState.getSagaStatus()).isEqualTo(SagaStatus.SAGA_COMPLETED);
        
        // Verify user profile created
        User userProfile = userRepository.findById(registeredUser.getId()).orElse(null);
        assertThat(userProfile).isNotNull();
        assertThat(userProfile.getEmail()).isEqualTo(request.getEmail());
    }
}
```

#### Contract Testing with Pact

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "user-management-service")
class EventContractTest {
    
    @Pact(consumer = "authentication-service")
    public RequestResponsePact userRegisteredEventContract(PactDslWithProvider builder) {
        return builder
            .given("user registration event is valid")
            .uponReceiving("a user registered event")
            .path("/api/events/user-registered")
            .method("POST")
            .headers(Map.of("Content-Type", "application/json"))
            .body(LambdaDsl.newJsonBody(body -> body
                .stringType("eventId")
                .stringValue("eventType", "USER_REGISTERED")
                .timestamp("timestamp", "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .object("payload", payload -> payload
                    .uuid("userId")
                    .stringType("email")
                    .stringType("firstName")
                    .stringType("lastName")
                )).build())
            .willRespondWith()
            .status(202)
            .body(LambdaDsl.newJsonBody(response -> response
                .stringValue("status", "ACCEPTED")
                .stringType("sagaId")
            ).build())
            .toPact();
    }
    
    @Test
    void shouldHandleUserRegisteredEvent(MockServer mockServer) {
        // Test implementation using the contract
    }
}
```

#### Performance Testing

```java
@Component
public class SagaPerformanceTest {
    
    @Test
    @DisplayName("Should handle 1000 concurrent saga executions")
    void shouldHandleConcurrentSagaExecutions() throws InterruptedException {
        int numberOfThreads = 100;
        int sagasPerThread = 10;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        
        List<Long> executionTimes = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < sagasPerThread; j++) {
                        long startTime = System.currentTimeMillis();
                        
                        // Execute saga
                        UserRegisteredEvent event = createTestEvent();
                        sagaOrchestrator.startUserRegistrationSaga(event);
                        
                        long endTime = System.currentTimeMillis();
                        executionTimes.add(endTime - startTime);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Analyze results
        double averageTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
            
        assertThat(averageTime).isLessThan(500); // Average should be under 500ms
        
        long p95 = executionTimes.stream()
            .sorted()
            .skip((long) (executionTimes.size() * 0.95))
            .findFirst()
            .orElse(0L);
            
        assertThat(p95).isLessThan(1000); // 95th percentile should be under 1s
    }
}
```

#### Chaos Engineering Tests

```java
@Component
public class SagaChaosTest {
    
    @Test
    @DisplayName("Should handle database failures gracefully")
    void shouldHandleDatabaseFailures() {
        // Simulate database failure
        Toxiproxy.getProxyOrNull("postgres").toxics()
            .latency("high-latency", ToxicDirection.DOWNSTREAM, 5000);
        
        UserRegisteredEvent event = createTestEvent();
        
        // Should handle timeout and initiate compensation
        assertThrows(SagaTimeoutException.class, () -> {
            sagaOrchestrator.startUserRegistrationSaga(event);
        });
        
        // Verify compensation was triggered
        SagaTransaction sagaTransaction = findSagaTransaction(event.getSagaId());
        assertThat(sagaTransaction.getStatus()).isEqualTo("COMPENSATED");
    }
    
    @Test
    @DisplayName("Should handle message broker failures")
    void shouldHandleMessageBrokerFailures() {
        // Simulate Redis failure
        redisContainer.stop();
        
        UserRegistrationRequest request = createTestRegistrationRequest();
        
        // Should handle gracefully and retry
        assertThrows(EventPublishingException.class, () -> {
            userRegistrationService.registerUser(request);
        });
        
        // Restart Redis and verify retry mechanism
        redisContainer.start();
        
        // Retry should succeed
        User user = userRegistrationService.registerUser(request);
        assertThat(user).isNotNull();
    }
}
```

### Saga Documentation and Runbooks

#### Operational Runbook

```markdown
# Saga Pattern Operational Runbook

## Common Issues and Solutions

### Issue: Saga Stuck in IN_PROGRESS State
**Symptoms**: Saga remains in IN_PROGRESS state for > 5 minutes
**Diagnosis**: Check saga_transactions table for stalled sagas
**Resolution**: 
1. Check if downstream service is responsive
2. Check message broker connectivity
3. Manually trigger compensation if needed

### Issue: High Compensation Rate
**Symptoms**: >10% of sagas requiring compensation
**Diagnosis**: Check error patterns in logs
**Resolution**:
1. Identify root cause (database, network, logic)
2. Implement circuit breaker if external service issue
3. Review saga step ordering and dependencies

### Issue: Message Broker Unavailable
**Symptoms**: Event publishing failures
**Diagnosis**: Check Redis/Azure Service Bus connectivity
**Resolution**:
1. Restart message broker service
2. Check network connectivity
3. Verify authentication credentials
4. Scale broker instances if needed
```

## Status Definitions (Actual Implementation)

### Authentication Service Saga States

| Status | Description | Implementation Location |
|--------|-------------|------------------------|
| `PENDING_REGISTRATION` | User registration request received | UserRegistrationService |
| `ACCOUNT_CREATED` | User account created in authentication service | EventPublishingService |
| `EVENT_PUBLISHED` | Registration event published to message broker | EventPublishingService |
| `EVENT_PUBLISH_FAILED` | Failed to publish registration event | EventPublishingService |
| `SAGA_STARTED` | Saga transaction started | SagaState enum |
| `SAGA_IN_PROGRESS` | Saga transaction in progress | SagaState enum |
| `SAGA_COMPLETED` | Saga transaction completed successfully | SagaState enum |
| `SAGA_FAILED` | Saga transaction failed | SagaState enum |

### User Management Service Processing States

| Status | Description | Implementation Location |
|--------|-------------|------------------------|
| `EVENT_RECEIVED` | Event received from message broker | EventHandlerService |
| `VALIDATION_IN_PROGRESS` | Event data validation in progress | SagaOrchestrator |
| `VALIDATION_PASSED` | Event data validation successful | SagaOrchestrator |
| `VALIDATION_FAILED` | Event data validation failed | SagaOrchestrator |
| `PROFILE_CREATION_IN_PROGRESS` | User profile creation in progress | UserEventService |
| `PROFILE_CREATED` | User profile created successfully | UserEventService |
| `PROFILE_CREATION_FAILED` | User profile creation failed | UserEventService |
| `DUPLICATE_USER_DETECTED` | Duplicate user detected | SagaOrchestrator |

## Configuration Properties (Actual Implementation)

### Authentication Service Configuration

```yaml
# application-local.yml
server:
  port: 8080

spring:
  application:
    name: authentication-service
  datasource:
    url: jdbc:postgresql://auth-postgres:5432/skishop_auth
    username: auth_user
    password: auth_password
    driver-class-name: org.postgresql.Driver
  data:
    redis:
      host: auth-redis
      port: 6379
      timeout: 2000ms

# Custom properties
skishop:
  runtime:
    event-propagation-enabled: true
    event-broker-type: redis
    event-max-retries: 3
    event-timeout-ms: 30000
    event-redis-key-prefix: skishop-local
    debug-mode: true
    environment: local

resilience4j:
  retry:
    instances:
      event-publishing:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
```

### User Management Service Configuration

```yaml
# application-local.yml
spring:
  application:
    name: user-management-service
  datasource:
    url: jdbc:postgresql://postgres:5432/skishop_user
    username: skishop_user
    password: password
    driver-class-name: org.postgresql.Driver
  data:
    redis:
      host: redis
      port: 6379
      timeout: 2000ms

server:
  port: 8081

# Custom properties
skishop:
  runtime:
    event-propagation-enabled: true
    event-broker-type: redis
    event-max-retries: 3
    event-timeout-ms: 30000
    event-redis-key-prefix: skishop-local
    debug-mode: true
    environment: local

saga:
  timeout:
    registration: 30
    deletion: 60
  max-retry: 3
```

## Environment Setup Instructions

### Local Development Environment

#### Prerequisites

- Java 21 or higher
- Maven 3.8+ or Gradle 8.0+
- Docker Desktop (for Redis and PostgreSQL)
- IDE (IntelliJ IDEA, Eclipse, VS Code, etc.)

#### 1. Start Infrastructure Services

**Create docker-compose.yml for local development:**

```yaml
version: '3.8'
services:
  # PostgreSQL for Authentication Service
  auth-postgres:
    image: postgres:15-alpine
    container_name: auth-postgres
    environment:
      POSTGRES_DB: skishop_auth
      POSTGRES_USER: auth_user
      POSTGRES_PASSWORD: auth_password
    ports:
      - "5433:5432"
    volumes:
      - auth_postgres_data:/var/lib/postgresql/data
    networks:
      - skishop-network

  # PostgreSQL for User Management Service
  user-postgres:
    image: postgres:15-alpine
    container_name: user-postgres
    environment:
      POSTGRES_DB: skishop_user
      POSTGRES_USER: skishop_user
      POSTGRES_PASSWORD: password
    ports:
      - "5434:5432"
    volumes:
      - user_postgres_data:/var/lib/postgresql/data
    networks:
      - skishop-network

  # Redis for event messaging
  redis:
    image: redis:7-alpine
    container_name: skishop-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - skishop-network
    command: redis-server --appendonly yes

  # Redis Commander for debugging
  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: redis-commander
    ports:
      - "8082:8081"
    environment:
      - REDIS_HOSTS=local:redis:6379
    networks:
      - skishop-network
    depends_on:
      - redis

volumes:
  auth_postgres_data:
  user_postgres_data:
  redis_data:

networks:
  skishop-network:
    driver: bridge
```

**Start services:**

```bash
# Create and start infrastructure
docker-compose up -d

# Verify services are running
docker-compose ps

# Check Redis connection
docker exec -it skishop-redis redis-cli ping
# Expected output: PONG
```

#### 2. Database Setup

**Create database schemas:**

```sql
-- Authentication Service Database Schema
-- Connect to auth_postgres
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    email_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    saga_type VARCHAR(100) NOT NULL,
    user_id UUID,
    status VARCHAR(50) NOT NULL,
    saga_status VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(255),
    original_event_id VARCHAR(255),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    timeout_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    last_heartbeat TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

```sql
-- User Management Service Database Schema
-- Connect to user_postgres
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(20),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS processed_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL,
    is_success BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id VARCHAR(255) UNIQUE NOT NULL,
    correlation_id VARCHAR(255),
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    processing_status VARCHAR(100),
    entity_id VARCHAR(255),
    context_data JSONB,
    steps_completed TEXT[],
    error_message TEXT,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    timeout_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0
);
```

#### 3. Build and Run Services

**Authentication Service:**

```bash
cd authentication-service

# Build the service
mvn clean compile

# Run with local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or with specific Redis host override
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.data.redis.host=localhost"
```

**User Management Service (in a separate terminal):**

```bash
cd user-management-service

# Build the service
mvn clean compile

# Run with local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or with specific Redis host override
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.data.redis.host=localhost"
```

#### 4. Verify the Setup

**Health Checks:**

```bash
# Check Authentication Service health
curl http://localhost:8080/actuator/health

# Check User Management Service health
curl http://localhost:8081/api/actuator/health

# Expected response:
# {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}
```

**Test Event Propagation:**

```bash
# Register a new user (this should trigger event propagation)
curl -X POST http://localhost:8080/api/auth/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }'

# Check Redis for event
docker exec -it skishop-redis redis-cli
127.0.0.1:6379> KEYS skishop-local:events:*
127.0.0.1:6379> LRANGE skishop-local:events:user_registered 0 -1

# Check if user was created in User Management Service
curl http://localhost:8081/api/users
```

**Monitor Events:**

```bash
# Monitor Redis events in real-time
docker exec -it skishop-redis redis-cli MONITOR

# Or use Redis Commander web interface
open http://localhost:8082
```

### Production Environment Configuration

#### Azure Resources Setup

**1. Create Azure Service Bus:**

```bash
# Set variables
RESOURCE_GROUP="skishop-prod"
LOCATION="eastus"
SERVICEBUS_NAMESPACE="skishop-servicebus"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Service Bus namespace
az servicebus namespace create \
  --resource-group $RESOURCE_GROUP \
  --name $SERVICEBUS_NAMESPACE \
  --location $LOCATION \
  --sku Standard

# Create topics
az servicebus topic create \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $SERVICEBUS_NAMESPACE \
  --name user-events

# Create subscriptions
az servicebus topic subscription create \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $SERVICEBUS_NAMESPACE \
  --topic-name user-events \
  --name user-management-subscription

# Get connection string
az servicebus namespace authorization-rule keys list \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $SERVICEBUS_NAMESPACE \
  --name RootManageSharedAccessKey \
  --query primaryConnectionString \
  --output tsv
```

**2. Create Azure Database for PostgreSQL:**

```bash
# Create PostgreSQL flexible server
az postgres flexible-server create \
  --resource-group $RESOURCE_GROUP \
  --name skishop-postgres \
  --location $LOCATION \
  --admin-user skishop \
  --admin-password "SecurePassword123!" \
  --sku-name Standard_B2s \
  --tier Burstable \
  --version 15 \
  --storage-size 32

# Configure firewall (allow Azure services)
az postgres flexible-server firewall-rule create \
  --resource-group $RESOURCE_GROUP \
  --name skishop-postgres \
  --rule-name allow-azure-services \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0
```

**3. Create Azure Redis Cache:**

```bash
# Create Redis cache
az redis create \
  --resource-group $RESOURCE_GROUP \
  --name skishop-redis \
  --location $LOCATION \
  --sku Standard \
  --vm-size c1

# Get Redis connection details
az redis show \
  --resource-group $RESOURCE_GROUP \
  --name skishop-redis \
  --query "{hostname:hostName,port:port,sslPort:sslPort}" \
  --output table

# Get access keys
az redis list-keys \
  --resource-group $RESOURCE_GROUP \
  --name skishop-redis \
  --output table
```

#### Production Environment Variables

**Create production configuration:**

```bash
# Create .env.production file
cat > .env.production << 'EOF'
# Spring Profiles
SPRING_PROFILES_ACTIVE=production

# Database Configuration
DATABASE_URL=jdbc:postgresql://skishop-postgres.postgres.database.azure.com:5432/postgres
DATABASE_USERNAME=skishop
DATABASE_PASSWORD=SecurePassword123!

# Azure Service Bus
AZURE_SERVICEBUS_CONNECTION_STRING=Endpoint=sb://skishop-servicebus.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=YOUR_KEY_HERE

# Redis
REDIS_HOST=skishop-redis.redis.cache.windows.net
REDIS_PORT=6380
REDIS_PASSWORD=YOUR_REDIS_KEY_HERE
REDIS_SSL_ENABLED=true

# Application Configuration
SKISHOP_EVENT_PROPAGATION_ENABLED=true
SKISHOP_EVENT_BROKER_TYPE=azure-servicebus
SKISHOP_EVENT_MAX_RETRIES=5
SKISHOP_EVENT_TIMEOUT_MS=60000
SKISHOP_DEBUG_MODE=false
SKISHOP_ENVIRONMENT=production

# Security
JWT_SECRET=production-secret-key-512-bits-long
ENCRYPTION_PASSWORD=production-encryption-password

# Azure App Service (if using)
WEBSITES_PORT=8080
EOF
```

## Troubleshooting Guide

### Common Issues and Solutions

#### 1. Redis Connection Issues

**Problem:** Cannot connect to Redis

```bash
# Check Redis status
docker ps | grep redis
docker logs skishop-redis

# Test Redis connection
docker exec -it skishop-redis redis-cli ping

# Check network connectivity
docker network ls
docker inspect skishop-network
```

**Solution:**

- Ensure Redis container is running
- Check network configuration
- Verify Redis host configuration in application.yml

#### 2. Event Not Being Consumed

**Problem:** Events published but not consumed

```bash
# Check Redis channels and messages
docker exec -it skishop-redis redis-cli
127.0.0.1:6379> PUBSUB CHANNELS
127.0.0.1:6379> PUBSUB NUMSUB skishop-local:events:user_registered

# Check application logs
docker logs authentication-service
docker logs user-management-service
```

**Solution:**

- Verify MessageListener configuration
- Check Redis channel names match between publisher and consumer
- Ensure User Management Service is subscribed to correct channels

#### 3. Database Connection Issues

**Problem:** Cannot connect to PostgreSQL

```bash
# Check PostgreSQL status
docker ps | grep postgres
docker logs auth-postgres
docker logs user-postgres

# Test database connection
docker exec -it auth-postgres psql -U auth_user -d skishop_auth -c "SELECT 1"
```

**Solution:**

- Verify database credentials in application-local.yml
- Check database host configuration
- Ensure database schemas are created

### Monitoring and Observability

#### Application Metrics

**Access monitoring endpoints:**

```bash
# Authentication Service metrics
curl http://localhost:8080/actuator/metrics

# User Management Service metrics
curl http://localhost:8081/api/actuator/metrics

# Specific metrics
curl http://localhost:8080/actuator/metrics/saga.active.count
curl http://localhost:8081/api/actuator/metrics/event.processing.duration
```

#### Log Analysis

**View application logs with filtering:**

```bash
# Authentication Service logs
docker logs authentication-service 2>&1 | grep -E "(EVENT|SAGA|ERROR)"

# User Management Service logs
docker logs user-management-service 2>&1 | grep -E "(EVENT|SAGA|ERROR)"

# Redis logs
docker logs skishop-redis 2>&1 | grep -E "(CLIENT|ERROR)"
```

## Verification and Validation Methods

This section provides comprehensive methods to verify and validate the event propagation system, saga implementation, and overall system functionality.

### 1. System Health Verification

#### Infrastructure Health Checks

**Database Connectivity:**

```bash
# PostgreSQL - Authentication Service
docker exec -it auth-postgres psql -U auth_user -d skishop_auth -c "SELECT 1 as healthy;"

# PostgreSQL - User Management Service  
docker exec -it user-postgres psql -U skishop_user -d skishop_user -c "SELECT 1 as healthy;"

# Expected output: healthy | 1
```

**Message Broker Health:**

```bash
# Redis health check
docker exec -it skishop-redis redis-cli ping
# Expected output: PONG

# Check Redis memory usage and connections
docker exec -it skishop-redis redis-cli INFO memory
docker exec -it skishop-redis redis-cli INFO clients

# Azure Service Bus health (for production)
az servicebus namespace show --resource-group $RESOURCE_GROUP --name $SERVICEBUS_NAMESPACE --query "status"
# Expected output: "Active"
```

**Application Health Endpoints:**

```bash
# Authentication Service detailed health
curl -s http://localhost:8080/actuator/health | jq '.'

# User Management Service detailed health  
curl -s http://localhost:8081/api/actuator/health | jq '.'

# Check specific health indicators
curl -s http://localhost:8080/actuator/health/redis | jq '.'
curl -s http://localhost:8080/actuator/health/db | jq '.'
```

### 2. Event Flow Verification

#### End-to-End Event Propagation Testing

**Complete User Registration Flow:**

```bash
#!/bin/bash
# test-event-propagation.sh

echo "=== Testing Complete Event Propagation ==="

# Step 1: Register a new user
echo "1. Registering new user..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser_'$(date +%s)'",
    "email": "test'$(date +%s)'@example.com", 
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }')

USER_ID=$(echo $RESPONSE | jq -r '.id')
echo "Created user with ID: $USER_ID"

# Step 2: Wait for event propagation
echo "2. Waiting for event propagation (5 seconds)..."
sleep 5

# Step 3: Verify user exists in Authentication Service
echo "3. Verifying user in Authentication Service..."
AUTH_USER=$(curl -s http://localhost:8080/api/auth/users/$USER_ID)
echo "Auth Service User: $(echo $AUTH_USER | jq -r '.email')"

# Step 4: Verify user profile created in User Management Service
echo "4. Verifying user profile in User Management Service..."
USER_PROFILE=$(curl -s http://localhost:8081/api/users/$USER_ID)
echo "User Management Service Profile: $(echo $USER_PROFILE | jq -r '.email')"

# Step 5: Verify saga state
echo "5. Checking saga state..."
SAGA_STATE=$(curl -s http://localhost:8080/api/auth/saga/user/$USER_ID)
echo "Saga Status: $(echo $SAGA_STATE | jq -r '.sagaStatus')"

# Step 6: Verify event in Redis
echo "6. Checking Redis events..."
docker exec -it skishop-redis redis-cli LRANGE skishop-local:events:user_registered -1 -1

echo "=== Event Propagation Test Complete ==="
```

**Event Message Validation:**

```bash
# Validate event structure and content
#!/bin/bash
# validate-events.sh

echo "=== Validating Event Messages ==="

# Get the latest event from Redis
LATEST_EVENT=$(docker exec -it skishop-redis redis-cli LRANGE skishop-local:events:user_registered -1 -1)

echo "Latest Event: $LATEST_EVENT"

# Validate event structure using jq
echo $LATEST_EVENT | jq '
{
  "hasEventId": has("eventId"),
  "hasEventType": has("eventType"), 
  "hasTimestamp": has("timestamp"),
  "hasPayload": has("payload"),
  "hasSagaId": has("sagaId"),
  "eventType": .eventType,
  "payloadUserId": .payload.userId,
  "payloadEmail": .payload.email
}'
```

### 3. Saga Transaction Verification

#### Saga State Validation

**Check Saga Transaction States:**

```sql
-- Connect to Authentication Service database
-- Check saga states
SELECT 
    saga_id,
    event_type,
    user_id,
    status,
    saga_status,
    start_time,
    end_time,
    retry_count,
    error_message
FROM saga_states 
WHERE created_at > NOW() - INTERVAL '1 hour'
ORDER BY created_at DESC;

-- Connect to User Management Service database  
-- Check saga transactions
SELECT 
    saga_id,
    correlation_id,
    saga_type,
    status,
    processing_status,
    entity_id,
    started_at,
    completed_at,
    retry_count,
    error_message
FROM saga_transactions 
WHERE started_at > NOW() - INTERVAL '1 hour'
ORDER BY started_at DESC;
```

**Saga Completion Rate Analysis:**

```sql
-- Authentication Service - Saga success rate
SELECT 
    saga_status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM saga_states 
WHERE created_at > NOW() - INTERVAL '24 hours'
GROUP BY saga_status;

-- User Management Service - Processing status distribution
SELECT 
    processing_status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage  
FROM saga_transactions
WHERE started_at > NOW() - INTERVAL '24 hours'
GROUP BY processing_status;
```

#### Compensation Logic Testing

**Simulate Failure Scenarios:**

```bash
#!/bin/bash
# test-compensation.sh

echo "=== Testing Compensation Logic ==="

# Test 1: Simulate database failure during profile creation
echo "1. Testing compensation for database failure..."

# Stop User Management Service database temporarily
docker stop user-postgres

# Try to register a user (should fail and trigger compensation)
curl -X POST http://localhost:8080/api/auth/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "failtest_'$(date +%s)'",
    "email": "failtest'$(date +%s)'@example.com",
    "password": "password123", 
    "firstName": "Fail",
    "lastName": "Test"
  }'

# Wait for compensation
sleep 10

# Restart database
docker start user-postgres
sleep 5

# Check saga state (should show compensation occurred)
echo "Checking compensation results..."
# Add verification logic here

echo "=== Compensation Test Complete ==="
```

### 4. Performance Verification

#### Throughput Testing

**Load Testing Script:**

```bash
#!/bin/bash
# load-test.sh

echo "=== Load Testing Event Propagation ==="

CONCURRENT_USERS=10
REQUESTS_PER_USER=5
TOTAL_REQUESTS=$((CONCURRENT_USERS * REQUESTS_PER_USER))

echo "Running $TOTAL_REQUESTS requests with $CONCURRENT_USERS concurrent users"

# Function to register a user
register_user() {
    local user_id=$1
    curl -s -X POST http://localhost:8080/api/auth/users \
      -H "Content-Type: application/json" \
      -d "{
        \"username\": \"loadtest_${user_id}_$(date +%s)\",
        \"email\": \"loadtest_${user_id}_$(date +%s)@example.com\",
        \"password\": \"password123\",
        \"firstName\": \"Load\",
        \"lastName\": \"Test${user_id}\"
      }" > /dev/null
}

# Run concurrent requests
start_time=$(date +%s)

for ((i=1; i<=CONCURRENT_USERS; i++)); do
    {
        for ((j=1; j<=REQUESTS_PER_USER; j++)); do
            register_user "${i}_${j}"
        done
    } &
done

wait

end_time=$(date +%s)
duration=$((end_time - start_time))

echo "Load test completed in ${duration} seconds"
echo "Throughput: $((TOTAL_REQUESTS / duration)) requests/second"

# Wait for event propagation
echo "Waiting for event propagation..."
sleep 30

# Verify results
echo "Verifying results..."
REDIS_EVENT_COUNT=$(docker exec -it skishop-redis redis-cli LLEN skishop-local:events:user_registered)
echo "Events in Redis: $REDIS_EVENT_COUNT"
```

#### Latency Measurement

**End-to-End Latency Testing:**

```bash
#!/bin/bash
# latency-test.sh

echo "=== Measuring End-to-End Latency ==="

for i in {1..10}; do
    echo "Test $i:"
    
    # Record start time
    start_time=$(date +%s%3N)
    
    # Register user
    USER_ID=$(curl -s -X POST http://localhost:8080/api/auth/users \
      -H "Content-Type: application/json" \
      -d '{
        "username": "latencytest_'$i'_'$(date +%s)'",
        "email": "latencytest_'$i'_'$(date +%s)'@example.com",
        "password": "password123",
        "firstName": "Latency",
        "lastName": "Test'$i'"
      }' | jq -r '.id')
    
    # Poll for user profile creation
    while true; do
        PROFILE=$(curl -s http://localhost:8081/api/users/$USER_ID)
        if echo $PROFILE | jq -e '.id' > /dev/null 2>&1; then
            end_time=$(date +%s%3N)
            latency=$((end_time - start_time))
            echo "  Latency: ${latency}ms"
            break
        fi
        sleep 0.1
    done
done
```

### 5. Data Consistency Verification

#### Cross-Service Data Validation

**Data Synchronization Check:**

```sql
-- Check for users that exist in auth but not in user management
WITH auth_users AS (
    SELECT id, email, first_name, last_name, created_at
    FROM users -- from authentication-service database
),
user_profiles AS (
    SELECT id, email, first_name, last_name, created_at  
    FROM users -- from user-management-service database
)
SELECT 
    a.id,
    a.email,
    'Missing in User Management Service' as issue
FROM auth_users a
LEFT JOIN user_profiles u ON a.id = u.id
WHERE u.id IS NULL
AND a.created_at > NOW() - INTERVAL '1 hour';

-- Check for orphaned user profiles
WITH auth_users AS (
    SELECT id, email
    FROM users -- from authentication-service database  
),
user_profiles AS (
    SELECT id, email
    FROM users -- from user-management-service database
)
SELECT 
    u.id,
    u.email,
    'Orphaned in User Management Service' as issue
FROM user_profiles u
LEFT JOIN auth_users a ON u.id = a.id
WHERE a.id IS NULL
AND u.created_at > NOW() - INTERVAL '1 hour';
```

**Data Integrity Validation:**

```bash
#!/bin/bash
# data-integrity-check.sh

echo "=== Data Integrity Verification ==="

# Get user count from Authentication Service
AUTH_COUNT=$(curl -s http://localhost:8080/api/auth/users/count)
echo "Users in Authentication Service: $AUTH_COUNT"

# Get user count from User Management Service
USER_COUNT=$(curl -s http://localhost:8081/api/users/count)
echo "Users in User Management Service: $USER_COUNT"

# Calculate difference
DIFF=$((AUTH_COUNT - USER_COUNT))
echo "Difference: $DIFF"

if [ $DIFF -eq 0 ]; then
    echo "✅ Data is synchronized"
elif [ $DIFF -gt 0 ]; then
    echo "⚠️  Authentication Service has $DIFF more users"
else
    echo "⚠️  User Management Service has $((-DIFF)) more users"
fi

# Check for recent discrepancies
echo "Checking recent saga failures..."
FAILED_SAGAS=$(curl -s http://localhost:8080/api/auth/saga/failed/count)
echo "Failed sagas in last 24h: $FAILED_SAGAS"
```

### 6. Error Handling Verification

#### Retry Mechanism Testing

**Test Event Publishing Retry:**

```bash
#!/bin/bash
# test-retry-mechanism.sh

echo "=== Testing Retry Mechanisms ==="

# Stop Redis to simulate broker failure
echo "1. Stopping Redis to simulate failure..."
docker stop skishop-redis

# Try to register user (should trigger retries)
echo "2. Attempting user registration (should retry)..."
curl -X POST http://localhost:8080/api/auth/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "retrytest_'$(date +%s)'",
    "email": "retrytest'$(date +%s)'@example.com",
    "password": "password123",
    "firstName": "Retry", 
    "lastName": "Test"
  }'

# Wait for retry attempts
echo "3. Waiting for retry attempts (30 seconds)..."
sleep 30

# Restart Redis
echo "4. Restarting Redis..."
docker start skishop-redis
sleep 5

# Check if event was eventually published
echo "5. Checking if event was published after Redis restart..."
sleep 10

EVENTS=$(docker exec -it skishop-redis redis-cli LLEN skishop-local:events:user_registered)
echo "Total events in queue: $EVENTS"
```

#### Circuit Breaker Testing

**Test Service Resilience:**

```bash
#!/bin/bash
# test-circuit-breaker.sh

echo "=== Testing Circuit Breaker Patterns ==="

# Simulate high error rate
echo "1. Simulating high error rate..."
for i in {1..20}; do
    # Make requests that will fail
    curl -X POST http://localhost:8080/api/auth/users \
      -H "Content-Type: application/json" \
      -d '{"invalid": "data"}' &
done

wait

# Check circuit breaker status
echo "2. Checking circuit breaker metrics..."
curl -s http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# Test if circuit breaker is open
echo "3. Testing if circuit breaker is open..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "cbtest",
    "email": "cbtest@example.com", 
    "password": "password123",
    "firstName": "CB",
    "lastName": "Test"
  }')

echo "Response code: $RESPONSE"
if [ $RESPONSE -eq 503 ]; then
    echo "✅ Circuit breaker is working (service unavailable)"
else
    echo "⚠️  Circuit breaker may not be working properly"
fi
```

### 7. Monitoring and Alerting Verification

#### Metrics Validation

**Check Application Metrics:**

```bash
#!/bin/bash
# verify-metrics.sh

echo "=== Verifying Metrics Collection ==="

# Check if metrics endpoints are accessible
echo "1. Checking metrics endpoints..."
curl -s http://localhost:8080/actuator/metrics | jq '.names[] | select(contains("saga"))'
curl -s http://localhost:8081/api/actuator/metrics | jq '.names[] | select(contains("event"))'

# Check specific saga metrics
echo "2. Checking saga-specific metrics..."
curl -s http://localhost:8080/actuator/metrics/saga.started.total
curl -s http://localhost:8080/actuator/metrics/saga.completed.total
curl -s http://localhost:8080/actuator/metrics/saga.failed.total

# Check event processing metrics  
echo "3. Checking event processing metrics..."
curl -s http://localhost:8081/api/actuator/metrics/event.processed.total
curl -s http://localhost:8081/api/actuator/metrics/event.processing.duration
```

**Prometheus Integration Check:**

```bash
#!/bin/bash
# verify-prometheus.sh

echo "=== Verifying Prometheus Integration ==="

# Check if Prometheus endpoints are working
echo "1. Checking Prometheus endpoints..."
curl -s http://localhost:8080/actuator/prometheus | grep saga
curl -s http://localhost:8081/api/actuator/prometheus | grep event

# Verify Prometheus is scraping metrics (if Prometheus is running)
if curl -s http://localhost:9090/api/v1/targets > /dev/null 2>&1; then
    echo "2. Checking Prometheus targets..."
    curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job | contains("auth")) | .health'
    curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job | contains("user")) | .health'
else
    echo "2. Prometheus not running, skipping target check"
fi
```

### 8. Security Verification

#### Authentication and Authorization Testing

**Test Event Source Validation:**

```bash
#!/bin/bash
# test-security.sh

echo "=== Security Verification ==="

# Test 1: Invalid event source
echo "1. Testing invalid event source rejection..."
curl -X POST http://localhost:8081/api/events/user-registered \
  -H "Content-Type: application/json" \
  -H "X-Event-Source: malicious-service" \
  -d '{
    "eventId": "fake-event-id",
    "eventType": "USER_REGISTERED",
    "producer": "malicious-service"
  }'

# Test 2: Malformed event payload
echo "2. Testing malformed event rejection..."
curl -X POST http://localhost:8081/api/events/user-registered \
  -H "Content-Type: application/json" \
  -d '{
    "malicious": "payload",
    "script": "<script>alert(\"xss\")</script>"
  }'

# Test 3: Check for sensitive data exposure
echo "3. Checking for sensitive data exposure..."
LOGS=$(docker logs authentication-service --tail 100 2>&1)
if echo "$LOGS" | grep -i "password"; then
    echo "⚠️  Warning: Sensitive data may be exposed in logs"
else
    echo "✅ No sensitive data found in logs"
fi
```

### 9. Automated Verification Scripts

#### Comprehensive Health Check

**Complete System Verification:**

```bash
#!/bin/bash
# comprehensive-verification.sh

echo "=========================================="
echo "    COMPREHENSIVE SYSTEM VERIFICATION    "
echo "=========================================="

SUCCESS_COUNT=0
TOTAL_TESTS=0

# Function to run test and track results
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    echo "Running: $test_name"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$test_command"; then
        echo "✅ PASS: $test_name"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo "❌ FAIL: $test_name"
    fi
    echo "---"
}

# Infrastructure tests
run_test "Redis Health" "docker exec skishop-redis redis-cli ping | grep -q PONG"
run_test "Auth DB Health" "docker exec auth-postgres pg_isready -U auth_user"
run_test "User DB Health" "docker exec user-postgres pg_isready -U skishop_user"

# Service health tests
run_test "Auth Service Health" "curl -s http://localhost:8080/actuator/health | jq -e '.status == \"UP\"'"
run_test "User Service Health" "curl -s http://localhost:8081/api/actuator/health | jq -e '.status == \"UP\"'"

# Functional tests
run_test "Event Propagation" "./test-event-propagation.sh"
run_test "Data Consistency" "./data-integrity-check.sh"
run_test "Metrics Collection" "./verify-metrics.sh"

# Calculate results
PASS_RATE=$((SUCCESS_COUNT * 100 / TOTAL_TESTS))

echo "=========================================="
echo "           VERIFICATION SUMMARY           "
echo "=========================================="
echo "Total Tests: $TOTAL_TESTS"
echo "Passed: $SUCCESS_COUNT"
echo "Failed: $((TOTAL_TESTS - SUCCESS_COUNT))"
echo "Pass Rate: ${PASS_RATE}%"

if [ $PASS_RATE -ge 90 ]; then
    echo "🎉 System verification PASSED"
    exit 0
elif [ $PASS_RATE -ge 70 ]; then
    echo "⚠️  System verification PARTIALLY PASSED"
    exit 1
else
    echo "💥 System verification FAILED"
    exit 2
fi
```

This comprehensive verification section provides multiple layers of validation to ensure the event propagation system and saga implementation are working correctly, from basic health checks to complex end-to-end scenarios.

## Security Considerations

### Event Security

1. **Payload Encryption** - Sensitive data in events should be encrypted
2. **Authentication** - Message broker access should be authenticated
3. **Authorization** - Services should validate event sources
4. **Data Masking** - Sensitive information in logs should be masked

### Network Security

1. **TLS/SSL** - All communication should use encrypted channels
2. **Private Networks** - Services should communicate over private networks
3. **Firewall Rules** - Appropriate firewall rules should be configured
4. **Access Control** - Implement proper access controls for message brokers

This document reflects the actual implementation found in the codebase and provides accurate setup instructions for both local development and production environments.

## Implementation Status

### ✅ Completed Features

1. **Event Publishing Service** - Authentication Service publishes events to Redis/Azure Service Bus
2. **Event Consumer Service** - User Management Service consumes events from message broker
3. **Saga Transaction Management** - Full saga orchestration with state tracking
4. **User Registration Flow** - Complete end-to-end user registration with event propagation
5. **User Deletion Flow** - Complete end-to-end user deletion with event propagation
6. **Status Feedback** - User Management Service sends status updates back to Authentication Service
7. **Error Handling** - Comprehensive error handling with retry mechanisms
8. **Configuration Management** - Environment-specific configurations for local and production
9. **Database Integration** - JPA entities and repositories for both services
10. **Docker Support** - Docker configurations for local development

### 🔄 Areas for Enhancement

1. **Azure Service Bus Integration** - Complete Azure Service Bus implementation for production
2. **Monitoring and Alerting** - Integration with Prometheus/Grafana for metrics
3. **Integration Tests** - End-to-end testing of event propagation
4. **Circuit Breaker** - Implementation of circuit breaker patterns for resilience
5. **Dead Letter Queue** - Implementation of dead letter queues for failed events
6. **Event Replay** - Capability to replay events for recovery scenarios
