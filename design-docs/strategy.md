# Enterprise Java Application Development Guide (Microservices Architecture)

## Table of Contents

1. [Architecture Design](#architecture-design)
2. [Microservices Architecture](#microservices-architecture)
3. [Development Methodology](#development-methodology)
4. [Performance and Scalability](#performance-and-scalability)
5. [Security](#security)
6. [Testing Strategy](#testing-strategy)
7. [Deployment and Operations](#deployment-and-operations)
8. [Non-functional Requirements](#non-functional-requirements)
9. [Best Practices](#best-practices)

## Architecture Design

### Layered Architecture

- **Presentation Layer**:  
  - User interfaces, REST APIs, and other external interfaces
  - Adoption of API Gateway and BFF (Backend For Frontend) patterns
  - Consideration of GraphQL for flexible data retrieval

- **Business Logic Layer**:  
  - Core application logic
  - Implementation based on Domain-Driven Design (DDD) principles
  - Consideration of CQRS (Command Query Responsibility Segregation) pattern

- **Data Access Layer**:  
  - Integration with databases and other persistent storage
  - Adoption of Repository pattern
  - Appropriate selection of ORM (JPA/Hibernate) or MyBatis

- **Infrastructure Layer**:  
  - Provides technical common functions (logging, authentication, monitoring, etc.)
  - Aggregation of cross-cutting concerns
  - Abstraction of external service integration

### Microservices vs. Monolith

- **Microservices**:
  - Divided into independent services, each focusing on specific business functions
  - Ability to adopt different technology stacks for different services
  - Improved scalability (scaling at service level)
  - Independent development between teams possible (Conway's Law)
  - Clear boundaries and responsibility separation between services
  - Partial deployment and rollback capabilities
  - Increased operational complexity (distributed system management complexity)
  - Need to ensure reliability of inter-service communication
  - Data consistency maintenance challenges (consideration of eventual consistency)

- **Monolith**:
  - Simple development and operations
  - Single deployment unit
  - Easy transaction processing (maintaining ACID properties)
  - Efficient communication between components through method calls
  - Scaling can be difficult (limitations of vertical scaling)
  - Partial feature updates are difficult

Microservices are not necessarily the optimal solution. The adoption of microservices architecture should be decided by comprehensively considering factors such as organizational size, project complexity, development team maturity, and business requirements.

## Microservices Architecture

Detailed important considerations when adopting microservices-based design.

### Service Decomposition Principles

- **Decomposition by Business Capability**:
  - Divide services based on organizational business functions
  - Examples: Order management, inventory management, customer management, payment processing, etc.

- **Decomposition by Subdomain (DDD)**:
  - Division based on Domain-Driven Design's Bounded Context
  - Each service has a clear domain model and scope of responsibility

- **Decomposition Granularity**:
  - Determining appropriate service size (avoid overly fine-grained division)
  - "Two Pizza Team" principle (each service can be developed by a team that can be fed with two pizzas)
  - Consider data cohesion and independence

### Inter-Service Communication

- **Synchronous Communication**:
  - REST API (OpenAPI/Swagger specification)
  - gRPC (Protocol Buffers)
  - GraphQL (client-optimized queries)

- **Asynchronous Communication**:
  - Message queues (RabbitMQ, Apache Kafka)
  - Event-driven architecture (Event Sourcing)
  - Command Query Responsibility Segregation (CQRS)

- **API Gateway**:
  - Intermediary between clients and microservices
  - Routing, authentication/authorization, SSL termination, load balancing
  - Rate limiting, caching, API aggregation
  - Utilization of Spring Cloud Gateway, Netflix Zuul, Kong, etc.

### Data Management Strategy

- **Database Selection**:
  - Independent databases per service (Database per Service)
  - Polyglot persistence (database selection based on service characteristics)
  - Appropriate selection between relational databases (PostgreSQL, MySQL) and non-relational databases (MongoDB, Cassandra, Redis)

- **Distributed Transactions**:
  - Transaction management across multiple services using Saga pattern
  - Avoiding two-phase commit (performance issues)
  - Accepting eventual consistency
  - Implementation of compensating transactions

- **Data Replication**:
  - Minimizing data sharing between services
  - Maintaining and updating redundant data as needed
  - Data synchronization using CDC (Change Data Capture)

### Service Discovery and Load Balancing

- **Service Registry**:
  - Dynamic registration and discovery of services
  - Utilizing Netflix Eureka, Consul, etcd, etc.
  - Integration with Spring Cloud Discovery

- **Client-side/Server-side Load Balancing**:
  - Spring Cloud LoadBalancer, Ribbon
  - Load balancing strategies between services

### Domain-Driven Design (DDD)

- **Strategic Design**:
  - Business domain-focused modeling
  - Establishment of ubiquitous language and promotion of common understanding
  - Definition and clarification of Bounded Contexts
  - Visualization of inter-service relationships through context maps

- **Tactical Design**:
  - Appropriate design of Aggregates
  - Clear role separation between entities, value objects, and repositories
  - Distinction between domain services and application services
  - Inter-service collaboration using domain events

- **Event Storming**:
  - Sharing domain model understanding across the entire team
  - Visualization of key business processes and event flows
  - Discovery and refinement of bounded contexts

### Cloud-Native Architecture

- **12-Factor Application**:
  - Codebase: Single codebase under version control
  - Dependencies: Explicitly declared and isolated dependencies
  - Config: Separation of code and configuration through environment variables
  - Backing Services: Treatment as attached resources
  - Build/Release/Run: Strictly separated stages
  - Processes: Stateless and share-nothing processes
  - Port Binding: Self-contained service exposure
  - Concurrency: Scale-out through process model
  - Disposability: Fast startup and graceful shutdown
  - Dev/Prod Parity: Maximum environment similarity
  - Logs: Log processing as event streams
  - Admin Processes: Running one-off processes

## Development Methodology

### Dependency Injection (DI)

- **Loose coupling between components**:
  - Breaking free from high coupling
  - Improved testability
  - Enhanced component reusability

- **DI Frameworks**:
  - Spring Framework (Spring Boot)
  - Google Guice
  - CDI (Contexts and Dependency Injection)

- **DI Implementation Patterns**:
  - Constructor injection (recommended)
  - Setter injection
  - Field injection (not recommended)

```java
// Dependency injection example (Spring Framework - Constructor injection)
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;
    
    @Autowired
    public UserServiceImpl(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }
    
    @Override
    public User registerUser(UserRegistrationRequest request) {
        // User registration logic
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setUsername(request.getUsername());
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        
        User savedUser = userRepository.save(newUser);
        
        // Send welcome email on successful registration
        emailService.sendWelcomeEmail(savedUser);
        
        return savedUser;
    }
}
```

### Interface-Oriented Design

- **Prioritizing interfaces over implementations**:
  - Clear contract definition
  - Separation from implementation details
  - Promoting loose coupling between modules

- **Leveraging polymorphism**:
  - Runtime implementation substitution
  - Improved extensibility and flexibility
  - Compatibility with design patterns like Strategy pattern

- **Impact on testing strategy**:
  - Easier testing with mock objects
  - Isolation of dependent components
  - Expanded unit testing coverage

```java
// Interface-oriented design example
public interface PaymentProcessor {
    PaymentResult processPayment(PaymentRequest paymentRequest);
    boolean supportsPaymentMethod(PaymentMethod method);
    PaymentStatus checkStatus(String transactionId);
}

@Service
public class CreditCardPaymentProcessor implements PaymentProcessor {
    private final PaymentGateway paymentGateway;
    
    @Autowired
    public CreditCardPaymentProcessor(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }
    
    @Override
    public PaymentResult processPayment(PaymentRequest paymentRequest) {
        // Credit card payment implementation
        return paymentGateway.processCardPayment(paymentRequest);
    }
    
    @Override
    public boolean supportsPaymentMethod(PaymentMethod method) {
        return method == PaymentMethod.CREDIT_CARD;
    }
    
    @Override
    public PaymentStatus checkStatus(String transactionId) {
        return paymentGateway.getTransactionStatus(transactionId);
    }
}

// Other implementations (PayPal, Apple Pay, etc.) also implement the same interface
```

### Exception Handling Strategy

- **Exception classification and appropriate usage**:
  - Checked exceptions (IOException, SQLException): Recoverable and require handling by caller
  - Runtime exceptions (IllegalArgumentException, NullPointerException): Program errors or improper usage
  - Custom exceptions: Application-specific error states

- **Global exception handling**:
  - Consistent error response format
  - Appropriate HTTP status code mapping
  - Security-conscious limitation of exposed exception information

- **Exception monitoring and logging**:
  - Structured error logs
  - Appropriate use of log levels
  - Centralized monitoring and analysis of exception information

- **Error notification to clients**:
  - User-friendly error messages
  - Detailed debug information only in development environments
  - Internationalized (i18n) error messages

```java
// Custom exception definition
public class ResourceNotFoundException extends RuntimeException {
    private final String resourceType;
    private final String resourceId;
    
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s with id %s not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getResourceId() {
        return resourceId;
    }
}

// Global exception handler example (Spring Boot)
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, 
                                                                         HttpServletRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Resource Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();
            
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex,
                                                                  HttpServletRequest request) {
        logger.warn("Validation error: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Error")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .validationErrors(ex.getValidationErrors())
            .build();
            
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    // Handling general exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex,
                                                              HttpServletRequest request) {
        logger.error("Unhandled exception occurred", ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred. Please try again later.")
            .path(request.getRequestURI())
            .build();
            
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

## Performance and Scalability

### Database Optimization

- **Index strategy**:
  - Appropriate index design based on query patterns
  - Effective use of composite indexes
  - Avoiding excessive index creation (impact on update performance)
  - Query plan analysis using EXPLAIN commands

- **Connection pooling**:
  - Adoption of high-performance connection pools like HikariCP
  - Appropriate pool size configuration (max/min/idle)
  - Connection leak detection and prevention
  - Connection acquisition wait time monitoring

- **Solving N+1 problems**:
  - Appropriate use of Eager Fetching
  - Utilizing JOIN FETCH clauses (JPA/Hibernate)
  - Using EntityGraph (JPA)
  - Optimizing batch fetch size

- **Transaction management**:
  - Setting appropriate transaction boundaries
  - Optimizing transaction isolation levels
  - Avoiding long-running transactions
  - Utilizing Optimistic Locking

- **Query optimization**:
  - Avoiding unnecessary data retrieval (avoiding SELECT *)
  - Implementing pagination (offset-based/keyset)
  - Strategic use of stored procedures
  - Considering query caching

### Caching Strategy

- **Multi-layer caching**:
  - HTTP level (browser cache, CDN)
  - Application level (method level, object level)
  - Database level (query cache, result set cache)

- **Cache technology selection**:
  - In-memory cache (Ehcache, Caffeine)
  - Distributed cache (Redis, Hazelcast, Apache Ignite)
  - Combined use of Near Cache and Remote Cache

- **Cache update strategies**:
  - Time-based invalidation (TTL: Time To Live)
  - Explicit invalidation (on CUD operations)
  - Event-driven invalidation (publish/subscribe)
  - Write-Through / Write-Behind patterns

- **Cache monitoring and management**:
  - Monitoring hit/miss rates
  - Tracking memory usage
  - Optimizing eviction policies
  - Cache warm-up strategies

```java
// Spring Cache example - multiple cache configurations and custom expiration
@Configuration
@EnableCaching
public class CachingConfig {
    
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        // Product cache - frequently read but rarely updated
        CaffeineCache productsCache = buildCache("products", 10000, Duration.ofHours(1));
        
        // Price cache - frequently updated
        CaffeineCache pricesCache = buildCache("prices", 10000, Duration.ofMinutes(5));
        
        // User profile cache - contains security information
        CaffeineCache userProfileCache = buildCache("userProfiles", 1000, Duration.ofMinutes(30));
        
        cacheManager.setCaches(Arrays.asList(productsCache, pricesCache, userProfileCache));
        return cacheManager;
    }
    
    private CaffeineCache buildCache(String name, int maxSize, Duration ttl) {
        return new CaffeineCache(name, 
            Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }
}

@Service
public class ProductServiceImpl implements ProductService {
    
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    
    @Autowired
    public ProductServiceImpl(ProductRepository productRepository, PriceRepository priceRepository) {
        this.productRepository = productRepository;
        this.priceRepository = priceRepository;
    }
    
    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) {
        // Database retrieval processing (executed only on cache miss)
        return productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
    }
    
    @Cacheable(value = "prices", key = "#productId")
    public Price getCurrentPrice(Long productId) {
        return priceRepository.findCurrentPriceByProductId(productId);
    }
    
    @CacheEvict(value = "products", key = "#product.id")
    @CachePut(value = "products", key = "#result.id")
    public Product updateProduct(Product product) {
        // Update processing
        return productRepository.save(product);
    }
    
    @CacheEvict(value = "prices", key = "#productId")
    public void updatePrice(Long productId, BigDecimal newPrice) {
        Price price = new Price();
        price.setProductId(productId);
        price.setAmount(newPrice);
        price.setEffectiveDate(LocalDateTime.now());
        priceRepository.save(price);
    }
}
```

### Asynchronous Processing and Batch Processing

- **Java Parallel Processing API**:
  - Effective use of CompletableFuture/CompletionStage
  - Thread pool optimization with ExecutorService
  - Understanding and utilizing ForkJoinPool characteristics
  - Appropriate use cases for parallel stream processing

- **Reactive Programming**:
  - Utilizing Spring WebFlux and Project Reactor
  - Leveraging benefits of non-blocking I/O
  - Understanding Reactive Streams API
  - Implementing and utilizing backpressure

- **Batch Processing Frameworks**:
  - Structuring batch jobs with Spring Batch
  - Efficient data processing with chunk-oriented processing
  - Job execution monitoring and restart capabilities
  - Parallel processing through partitioning

- **Messaging System Integration**:
  - Asynchronous messaging (RabbitMQ, Apache Kafka)
  - Durable message delivery
  - Dead letter queue implementation
  - Ensuring idempotency

```java
// Asynchronous processing example using CompletableFuture
@Service
public class OrderProcessingService {
    
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;
    private final NotificationService notificationService;
    private final Executor asyncExecutor;
    
    @Autowired
    public OrderProcessingService(
            InventoryService inventoryService,
            PaymentService paymentService,
            ShippingService shippingService,
            NotificationService notificationService,
            @Qualifier("taskExecutor") Executor asyncExecutor) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.shippingService = shippingService;
        this.notificationService = notificationService;
        this.asyncExecutor = asyncExecutor;
    }
    
    public CompletableFuture<OrderResult> processOrderAsync(Order order) {
        // Execute inventory check and payment processing in parallel
        CompletableFuture<InventoryResult> inventoryCheck = CompletableFuture
            .supplyAsync(() -> inventoryService.checkInventory(order.getItems()), asyncExecutor);
            
        CompletableFuture<PaymentResult> paymentProcess = CompletableFuture
            .supplyAsync(() -> paymentService.processPayment(order), asyncExecutor);
            
        // Execute shipping processing when both processes complete
        return inventoryCheck.thenCombineAsync(paymentProcess, (invResult, payResult) -> {
            if (!invResult.isAvailable()) {
                throw new InsufficientInventoryException(invResult.getUnavailableItems());
            }
            
            if (!payResult.isSuccessful()) {
                throw new PaymentFailedException(payResult.getErrorCode());
            }
            
            // Arrange shipping
            ShippingResult shippingResult = shippingService.arrangeShipping(order);
            
            // Send notification asynchronously (don't wait for result)
            CompletableFuture.runAsync(() -> 
                notificationService.sendOrderConfirmation(order, shippingResult), asyncExecutor);
                
            return new OrderResult(order.getId(), OrderStatus.PROCESSING, 
                                  shippingResult.getTrackingNumber(), 
                                  shippingResult.getEstimatedDelivery());
        }, asyncExecutor);
    }
}

// Batch processing example using Spring Batch
@Configuration
@EnableBatchProcessing
public class MonthlyReportBatchConfig {
    
    @Autowired
    private JobBuilderFactory jobBuilderFactory;
    
    @Autowired
    private StepBuilderFactory stepBuilderFactory;
    
    @Autowired
    private DataSource dataSource;
    
    @Bean
    public Job generateMonthlyReportJob(Step generateReportStep) {
        return jobBuilderFactory.get("generateMonthlyReportJob")
            .incrementer(new RunIdIncrementer())
            .listener(new JobCompletionNotificationListener())
            .flow(generateReportStep)
            .end()
            .build();
    }
    
    @Bean
    public Step generateReportStep(ItemReader<Transaction> reader,
                                 ItemProcessor<Transaction, ReportEntry> processor,
                                 ItemWriter<ReportEntry> writer) {
        return stepBuilderFactory.get("generateReportStep")
            .<Transaction, ReportEntry>chunk(100) // Process 100 records at a time
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(10)
            .skip(DataIntegrityViolationException.class)
            .build();
    }
    
    @Bean
    public JdbcCursorItemReader<Transaction> reader() {
        JdbcCursorItemReader<Transaction> reader = new JdbcCursorItemReader<>();
        reader.setDataSource(dataSource);
        reader.setSql("SELECT id, date, amount, customer_id, type FROM transactions " +
                     "WHERE date BETWEEN :startDate AND :endDate");
        reader.setRowMapper(new TransactionRowMapper());
        reader.setParameterValues(Collections.singletonMap("startDate", 
                                 LocalDate.now().minusMonths(1).withDayOfMonth(1),
                                 "endDate", LocalDate.now().withDayOfMonth(1).minusDays(1)));
        return reader;
    }
    
    // Processor and Writer definitions are omitted
}
```

### Scalability

- **Horizontal scaling design**:
  - Thorough stateless architecture
  - Dynamic scaling of service instances
  - Application startup time optimization (JVM tuning, Spring Boot optimization)
  - Load balancing algorithm considerations (round robin, least connections, resource-based)

- **Distributed session management**:
  - Session sharing mechanisms (Redis, Hazelcast)
  - Stateless authentication with JWT
  - Session replication strategies
  - Sticky sessions vs. distributed sessions

- **Container orchestration**:
  - Auto-scaling with Kubernetes
  - Appropriate resource requests and limits configuration
  - Horizontal Pod Autoscaler (HPA) configuration
  - Rolling update strategies

- **Database scaling**:
  - Utilizing read replicas
  - Sharding strategies (horizontal partitioning)
  - Appropriate sizing of database connection pools
  - Selective adoption of NoSQL solutions

## Security

### Authentication and Authorization

- **Modern authentication protocols**:
  - Appropriate selection of OAuth 2.0 flows (authorization code, client credentials, etc.)
  - Authentication and information provision with OpenID Connect
  - Secure JWT (JSON Web Token) implementation
  - MFA implementation (TOTP, FIDO2, WebAuthn)

- **Security frameworks**:
  - Latest Spring Security practices
  - Integration with IDaaS (Identity as a Service) like Keycloak
  - Security context propagation (between microservices)
  - Client-side security (CSP, SameSite Cookie)

- **Access control models**:
  - RBAC (Role-Based Access Control) implementation
  - ABAC (Attribute-Based Access Control) adoption considerations
  - Access decision delegation patterns
  - Application of least privilege principle

- **API security**:
  - API key/client secret management
  - Rate limiting and throttling
  - Mutual authentication with Mutual TLS (mTLS)
  - Centralized security policy management through API Gateway

```java
// Authentication and authorization example using Spring Security
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // CSRF protection is often unnecessary for RESTful APIs
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/users/{userId}/**").access(this::checkUserAccess)
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            );
            
        return http.build();
    }
    
    private AuthorizationDecision checkUserAccess(Supplier<Authentication> authentication, 
                                                RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        String userId = context.getVariables().get("userId");
        
        // Allow access only when accessing own data or having ADMIN privileges
        if (auth.getName().equals(userId) || auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return new AuthorizationDecision(true);
        }
        
        return new AuthorizationDecision(false);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Using Argon2id (latest secure hashing algorithm)
        return new Argon2PasswordEncoder(16, 32, 1, 16384, 2);
    }
}

// Method-level security
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    
    private final ProjectService projectService;
    
    @Autowired
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }
    
    @GetMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or @projectSecurityService.hasProjectAccess(authentication, #projectId)")
    public ResponseEntity<Project> getProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProjectById(projectId));
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<Project> createProject(@Valid @RequestBody ProjectRequest projectRequest) {
        Project created = projectService.createProject(projectRequest);
        return ResponseEntity.created(URI.create("/api/projects/" + created.getId())).body(created);
    }
    
    @PutMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or " +
                 "(hasRole('PROJECT_MANAGER') and @projectSecurityService.isProjectManager(authentication, #projectId))")
    public ResponseEntity<Project> updateProject(@PathVariable Long projectId,
                                               @Valid @RequestBody ProjectRequest projectRequest) {
        Project updated = projectService.updateProject(projectId, projectRequest);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }
}
```

### Data Protection

- **Encryption strategies**:
  - Transport layer encryption (TLS 1.3)
  - Data at rest encryption (transparent encryption, field-level encryption)
  - Appropriate selection of encryption algorithms (AES-GCM, ChaCha20-Poly1305)
  - Key management (rotation, storage, access control)

- **Sensitive information protection**:
  - Password hashing (Argon2id, bcrypt)
  - Personal Identifiable Information (PII) protection
  - Sensitive information masking and tokenization
  - Utilizing secret management tools like Vault

- **Privacy compliance**:
  - Regulatory compliance (GDPR, CCPA, etc.)
  - Application of data minimization principles
  - Explicit consent management
  - Data deletion and anonymization mechanisms

```java
// Data encryption example
@Configuration
public class EncryptionConfig {
    
    @Value("${encryption.secret}")
    private String encryptionSecret;
    
    @Value("${encryption.salt}")
    private String salt;
    
    @Bean
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(encryptionSecret);
        config.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        config.setKeyObtentionIterations(1000);
        config.setPoolSize(4);
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);
        return encryptor;
    }
}

// Using encrypted fields in JPA entities
@Entity
@Table(name = "customers")
public class Customer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    private String email;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ssn")
    private String socialSecurityNumber; // Encrypted
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "credit_card")
    private String creditCardNumber;     // Encrypted
    
    // Getters and setters
}

// Custom encryption converter
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    
    private static StringEncryptor encryptor;
    
    @Autowired
    public void setEncryptor(StringEncryptor encryptor) {
        EncryptedStringConverter.encryptor = encryptor;
    }
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return encryptor.encrypt(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptor.decrypt(dbData);
    }
}
```

### Secure Coding

- **OWASP Top 10 countermeasures**:
  - Injection attack prevention (SQL, NoSQL, OS Command, LDAP)
  - XSS prevention (Content-Security-Policy, output encoding)
  - CSRF prevention (token-based, SameSite Cookie)
  - Server-Side Request Forgery (SSRF) prevention

- **Input validation and output encoding**:
  - Whitelist-based input validation
  - Multi-layer validation (client-side and server-side)
  - Context-dependent escape processing
  - Appropriate header settings (Content-Type, X-Content-Type-Options, etc.)

- **Security testing**:
  - Static analysis tools (SAST) adoption (SonarQube, FindBugs)
  - Dynamic analysis tools (DAST) utilization (OWASP ZAP)
  - Dependency vulnerability scanning (OWASP Dependency-Check)
  - Regular penetration testing

- **Security monitoring**:
  - Anomaly detection and alerting
  - Centralized log management and analysis
  - Security incident response plans
  - Compliance audit support

```java
// Secure REST controller example
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    
    private final UserService userService;
    private final XSSProtectionService xssProtectionService;
    
    @Autowired
    public UserController(UserService userService, XSSProtectionService xssProtectionService) {
        this.userService = userService;
        this.xssProtectionService = xssProtectionService;
    }
    
    @GetMapping
    public ResponseEntity<List<UserDTO>> getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Pattern(regexp = "^[a-zA-Z0-9,]*$") String fields) {
        
        // Field safety check (SQL injection prevention)
        List<String> safeFields = null;
        if (fields != null) {
            safeFields = Arrays.stream(fields.split(","))
                .filter(field -> field.matches("^[a-zA-Z0-9_]+$"))
                .collect(Collectors.toList());
        }
        
        List<UserDTO> users = userService.getUsers(page, size, safeFields);
        return ResponseEntity.ok(users);
    }
    
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody UserCreationRequest request) {
        // XSS prevention: sanitize user input data
        UserCreationRequest sanitizedRequest = new UserCreationRequest(
            xssProtectionService.sanitize(request.getUsername()),
            request.getEmail(), // Email address is already validated
            request.getPassword(), // Don't sanitize passwords
            xssProtectionService.sanitize(request.getDisplayName())
        );
        
        UserDTO createdUser = userService.createUser(sanitizedRequest);
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(createdUser.getId())
            .toUri();
            
        return ResponseEntity.created(location).body(createdUser);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(
            @PathVariable @Pattern(regexp = "^[0-9]+$") String id) {
        // Input validation (also applies to path parameters)
        if (!id.matches("^[0-9]+$")) {
            throw new ValidationException("Invalid user ID format");
        }
        
        UserDTO user = userService.getUserById(Long.parseLong(id));
        return ResponseEntity.ok(user);
    }
    
    // Actual user service implementation should include:
    // - SQL injection prevention with PreparedStatement
    // - Secure password hashing
    // - Authorization checks
    // - Rate limiting
}

// Input validation example (Bean Validation)
public class UserCreationRequest {
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters long")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain alphanumeric characters and underscores")
    private String username;
    
    @NotBlank(message = "Email address is required")
    @Email(message = "Please enter a valid email address")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$",
            message = "Password must be at least 8 characters long and contain at least one digit, lowercase letter, uppercase letter, and special character")
    private String password;
    
    @Size(max = 100, message = "Display name must be 100 characters or less")
    private String displayName;
    
    // Constructors, getters, setters
}
```

## Testing Strategy

### Unit Testing

- **Testing frameworks and auxiliary tools**:
  - Utilizing JUnit 5 new features (@ParameterizedTest, @Nested, Extensions)
  - Advanced Mockito 3/4 features (ArgumentCaptor, BDDMockito)
  - Readable assertions with AssertJ/Hamcrest
  - Adoption of test data builder patterns

- **Test design methods**:
  - Boundary value analysis and equivalence partitioning
  - Error cases and exception testing
  - Diverse scenarios with parameterized tests
  - Selective adoption of Test-Driven Development (TDD)

- **Test coverage**:
  - Measuring branch coverage as well as line coverage
  - Test quality evaluation with Mutation Testing
  - Focused testing on critical business logic
  - JaCoCo and other coverage tool integration with CI

- **Developer experience**:
  - Fast test execution (Spring Boot Test Slices, test isolation)
  - Enhancing test reliability (eliminating flaky tests)
  - Automated fix suggestions (IDE plugins)
  - Cultivating test culture (pair programming, code reviews)

```java
// Comprehensive unit testing example using JUnit 5 and Mockito
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentService paymentService;
    
    @Mock
    private InventoryService inventoryService;
    
    @Mock
    private NotificationService notificationService;
    
    @InjectMocks
    private OrderServiceImpl orderService;
    
    @Nested
    @DisplayName("Order Creation Tests")
    class CreateOrderTests {
        
        @Test
        @DisplayName("For valid order request, order should be created successfully")
        void createOrder_WithValidRequest_ShouldCreateOrder() {
            // Arrange
            OrderRequest request = new OrderRequestBuilder()
                .withCustomerId(1L)
                .withItems(List.of(
                    new OrderItemRequest(101L, 2),
                    new OrderItemRequest(102L, 1)
                ))
                .withPaymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
                
            Customer customer = new CustomerBuilder().withId(1L).build();
            
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(inventoryService.checkAvailability(anyList())).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L); // Set ID
                return order;
            });
            when(paymentService.processPayment(any(), any())).thenReturn(
                new PaymentResult(true, "PAYMENT-123", null));
                
            // Act
            OrderResult result = orderService.createOrder(request);
            
            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getOrderId()).isEqualTo(1L);
            
            // Verify dependent service calls
            verify(inventoryService).checkAvailability(anyList());
            verify(orderRepository).save(any(Order.class));
            verify(paymentService).processPayment(any(), any());
            verify(notificationService).sendOrderConfirmation(any(Order.class));
        }
        
        @Test
        @DisplayName("When inventory is insufficient, exception should be thrown")
        void createOrder_WithInsufficientInventory_ShouldThrowException() {
            // Arrange
            OrderRequest request = new OrderRequestBuilder()
                .withCustomerId(1L)
                .withItems(List.of(new OrderItemRequest(101L, 100))) // Large order
                .withPaymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
                
            Customer customer = new CustomerBuilder().withId(1L).build();
            
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(inventoryService.checkAvailability(anyList())).thenReturn(false);
            
            // 実行と検証
            assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("在庫不足");
                
            // 依存サービスの呼び出し検証
            verify(inventoryService).checkAvailability(anyList());
            verify(orderRepository, never()).save(any());
            verify(paymentService, never()).processPayment(any(), any());
            verify(notificationService, never()).sendOrderConfirmation(any());
        }
        
        @ParameterizedTest
        @DisplayName("For invalid order items, ValidationException should be thrown")
        @MethodSource("invalidOrderItemsProvider")
        void createOrder_WithInvalidItems_ShouldThrowValidationException(
                List<OrderItemRequest> invalidItems, String expectedErrorMessage) {
            // 準備
            OrderRequest request = new OrderRequestBuilder()
                .withCustomerId(1L)
                .withItems(invalidItems)
                .withPaymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
                
            // 実行と検証
            assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(expectedErrorMessage);
        }
        
        static Stream<Arguments> invalidOrderItemsProvider() {
            return Stream.of(
                Arguments.of(Collections.emptyList(), "少なくとも1つの商品"),
                Arguments.of(null, "注文項目"),
                Arguments.of(List.of(new OrderItemRequest(null, 1)), "商品ID"),
                Arguments.of(List.of(new OrderItemRequest(101L, 0)), "1以上"),
                Arguments.of(List.of(new OrderItemRequest(101L, -1)), "1以上")
            );
        }
    }
    
    // 他のテストケース（注文取得、キャンセルなど）
}
```

### Integration Testing

- **テスト環境の整備**:
  - Spring Test Frameworkの効果的な活用
  - Testcontainersによる一時的なインフラ提供（DB, Redis, Kafka等）
  - テストプロファイルと環境固有の設定
  - テストデータ準備とクリーンアップの自動化

- **統合テスト範囲**:
  - データアクセス層テスト（リポジトリテスト）
  - コントローラテスト（WebMvcTest, WebTestClient）
  - 外部サービス連携テスト（API Clients, Feign）
  - 非同期処理/イベント処理テスト

- **API契約テスト**:
  - Spring Cloud Contractによる契約テスト
  - Pactoによるコンシューマ駆動契約テスト
  - OpenAPI/Swaggerによる仕様準拠テスト
  - モックサーバー（WireMock）の活用

```java
// Spring BootとTestcontainersを使用した統合テスト例
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderRepositoryIntegrationTest {
    
    // PostgreSQL用のTestcontainers設定
    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:14")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withInitScript("schema.sql");
        
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private TestEntityManager entityManager;
    
    @BeforeEach
    void setUp() {
        // テストデータ準備
        Customer customer = new Customer();
        customer.setName("Test Customer");
        customer.setEmail("customer@example.com");
        entityManager.persist(customer);
        
        Product product1 = new Product();
        product1.setName("Product 1");
        product1.setPrice(new BigDecimal("10.99"));
        entityManager.persist(product1);
        
        Product product2 = new Product();
        product2.setName("Product 2");
        product2.setPrice(new BigDecimal("20.50"));
        entityManager.persist(product2);
        
        entityManager.flush();
    }
    
    @Test
    @DisplayName("注文を保存して取得する")
    void saveAndFindOrder() {
        // 準備
        Customer customer = entityManager.find(Customer.class, 1L);
        List<Product> products = entityManager.getEntityManager()
            .createQuery("SELECT p FROM Product p", Product.class)
            .getResultList();
            
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.CREATED);
        order.setOrderDate(LocalDateTime.now());
        
        List<OrderItem> items = new ArrayList<>();
        OrderItem item1 = new OrderItem();
        item1.setOrder(order);
        item1.setProduct(products.get(0));
        item1.setQuantity(2);
        item1.setPrice(products.get(0).getPrice());
        items.add(item1);
        
        OrderItem item2 = new OrderItem();
        item2.setOrder(order);
        item2.setProduct(products.get(1));
        item2.setQuantity(1);
        item2.setPrice(products.get(1).getPrice());
        items.add(item2);
        
        order.setItems(items);
        
        // 実行
        Order savedOrder = orderRepository.save(order);
        Optional<Order> foundOrder = orderRepository.findById(savedOrder.getId());
        
        // 検証
        assertThat(foundOrder).isPresent();
        Order retrievedOrder = foundOrder.get();
        assertThat(retrievedOrder.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(retrievedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(retrievedOrder.getItems()).hasSize(2);
        
        // 注文金額の計算が正しいことを検証
        BigDecimal expectedTotal = new BigDecimal("42.48"); // 10.99*2 + 20.50
        assertThat(retrievedOrder.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    }
    
    @Test
    @DisplayName("特定の顧客の注文を検索する")
    void findOrdersByCustomerId() {
        // 準備 - 同じ顧客の注文を複数作成
        Customer customer = entityManager.find(Customer.class, 1L);
        
        // 注文1
        Order order1 = new Order();
        order1.setCustomer(customer);
        order1.setStatus(OrderStatus.DELIVERED);
        order1.setOrderDate(LocalDateTime.now().minusDays(10));
        entityManager.persist(order1);
        
        // 注文2
        Order order2 = new Order();
        order2.setCustomer(customer);
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setOrderDate(LocalDateTime.now().minusDays(5));
        entityManager.persist(order2);
        
        // 注文3 - 別の顧客用
        Customer customer2 = new Customer();
        customer2.setName("Other Customer");
        customer2.setEmail("other@example.com");
        entityManager.persist(customer2);
        
        Order order3 = new Order();
        order3.setCustomer(customer2);
        order3.setStatus(OrderStatus.CREATED);
        order3.setOrderDate(LocalDateTime.now());
        entityManager.persist(order3);
        
        entityManager.flush();
        
        // 実行
        List<Order> customerOrders = orderRepository.findByCustomerId(customer.getId());
        
        // 検証
        assertThat(customerOrders).hasSize(2);
        assertThat(customerOrders).extracting(Order::getCustomer)
            .extracting(Customer::getId)
            .containsOnly(customer.getId());
    }
}
```

### パフォーマンステスト

- **パフォーマンスの種類**:
  - 負荷テスト（Load Testing）：一定量の負荷下での動作検証
  - ストレステスト（Stress Testing）：限界点の発見
  - 耐久テスト（Endurance Testing）：長時間実行時の安定性
  - スパイクテスト（Spike Testing）：急激な負荷変動への対応

- **テストツール**:
  - JMeterによる複雑なテストシナリオ作成
  - Gatlingによるスケーラブルな負荷テスト（Scala DSL）
  - K6によるモダンなJavaScriptベースの負荷テスト
  - Locustによる分散負荷テスト

- **パフォーマンス指標**:
  - レスポンスタイム（平均、90パーセンタイル、99パーセンタイル）
  - スループット（RPS/TPS）
  - エラー率
  - リソース使用率（CPU, メモリ, ディスクI/O, ネットワーク）

- **テスト環境と実施**:
  - 本番に近い環境での実施
  - CI/CDパイプラインへの組み込み
  - ベースラインとの比較分析
  - ボトルネック特定と改善サイクル

```groovy
// Gatlingを使用したパフォーマンステストの例（Scala DSL）
package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class OrderAPISimulation extends Simulation {
  
  // HTTP設定
  val httpProtocol = http
    .baseUrl("https://api.example.com")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling Performance Test")
  
  // テストデータフィーダー
  val productIds = csv("products.csv").random
  
  // 共通ヘッダー（認証トークンなど）
  val headers = Map(
    "Authorization" -> "Bearer ${token}"
  )
  
  // シナリオ定義
  val createOrderScenario = scenario("Create Order API Test")
    .exec(session => session.set("token", generateToken()))
    .feed(productIds)
    .exec(
      http("Get Product Details")
        .get("/products/${productId}")
        .headers(headers)
        .check(status.is(200))
        .check(jsonPath("$.name").saveAs("productName"))
        .check(jsonPath("$.price").saveAs("productPrice"))
    )
    .pause(1, 3)
    .exec(
      http("Add to Cart")
        .post("/cart/items")
        .headers(headers)
        .body(StringBody("""{"productId": ${productId}, "quantity": 1}"""))
        .check(status.is(201))
    )
    .pause(2, 5)
    .exec(
      http("Get Cart")
        .get("/cart")
        .headers(headers)
        .check(status.is(200))
        .check(jsonPath("$.items[0].productId").is("${productId}"))
    )
    .pause(3, 7)
    .exec(
      http("Create Order")
        .post("/orders")
        .headers(headers)
        .body(StringBody("""
          {
            "paymentMethod": "CREDIT_CARD",
            "shippingAddress": {
              "street": "123 Test St",
              "city": "Test City",
              "country": "Test Country",
              "postalCode": "12345"
            }
          }
        """))
        .check(status.is(201))
        .check(jsonPath("$.orderId").saveAs("orderId"))
    )
    .pause(1, 2)
    .exec(
      http("Get Order Details")
        .get("/orders/${orderId}")
        .headers(headers)
        .check(status.is(200))
        .check(jsonPath("$.status").is("PROCESSING"))
    )
  
  // 負荷プロファイル定義
  setUp(
    // 通常負荷テスト：5分間かけて100ユーザーまでランプアップし、10分間維持
    createOrderScenario.inject(
      rampUsers(100).during(5.minutes),
      constantUsers(100).during(10.minutes)
    )
    // ストレステスト：上記の負荷に加えて、さらに200ユーザーを2分間で追加
    /*
    createOrderScenario.inject(
      rampUsers(100).during(5.minutes),
      constantUsers(100).during(10.minutes),
      rampUsers(200).during(2.minutes),
      constantUsers(300).during(5.minutes)
    )
    */
    // スパイクテスト：突然の大量リクエスト
    /*
    createOrderScenario.inject(
      constantUsers(10).during(2.minutes),
      nothingFor(10.seconds),
      atOnceUsers(100),
      nothingFor(30.seconds),
      constantUsers(10).during(2.minutes)
    )
    */
  ).protocols(httpProtocol)
    .assertions(
      // 成功率が99.9%以上
      global.successfulRequests.percent.gte(99.9),
      // 95%のリクエストが1秒以内に完了
      global.responseTime.percentile3.lte(1000),
      // 99%のリクエストが2秒以内に完了
      global.responseTime.percentile4.lte(2000)
    )
}
```

### Continuous Integration

- **CI環境構築**:
  - GitHub Actions/GitLab CI/CircleCI/Jenkins等の選定と設定
  - マルチステージパイプラインの設計（ビルド、テスト、静的解析、セキュリティスキャン）
  - セルフホスト型ランナーとクラウドランナーの使い分け
  - パイプラインの並列化と最適化

- **コード品質ゲート**:
  - SonarQubeによる包括的なコード品質分析
  - チェックスタイル、PMD、SpotBugsによる静的解析
  - Dependabotによる依存関係の脆弱性チェック
  - ブランチ保護とPRの自動レビュー

- **テスト自動化**:
  - ユニットテストの並列実行
  - ピラミッド型テスト戦略（Unit > Integration > E2E）
  - フレーキーテストの特定と修正
  - テスト結果の可視化と通知

- **開発プラクティス**:
  - トランクベース開発の検討
  - フィーチャーフラグの活用
  - テスト駆動開発（TDD）の選択的導入
  - ビヘイビア駆動開発（BDD）による要件と実装の整合性確保

```yaml
# GitHub Actionsのワークフロー例
name: Java CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    services:
      # テスト用のPostgreSQL
      postgres:
        image: postgres:14
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
          POSTGRES_DB: testdb
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      
      # テスト用のRedis
      redis:
        image: redis:6
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
    - uses: actions/checkout@v3
      with:
        fetch-depth: 0  # Complete history for SonarQube
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Build and Test
      run: mvn -B verify
    
    - name: Run SonarQube Analysis
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: >
        mvn sonar:sonar
        -Dsonar.projectKey=my-project
        -Dsonar.organization=my-org
        -Dsonar.host.url=https://sonarcloud.io
    
    - name: Generate JaCoCo Badge
      uses: cicirello/jacoco-badge-generator@v2
      with:
        generate-branches-badge: true
        jacoco-csv-file: target/site/jacoco/jacoco.csv
    
    - name: Upload Test Results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: test-results
        path: |
          target/surefire-reports
          target/failsafe-reports
    
    - name: Upload JaCoCo Coverage Report
      uses: actions/upload-artifact@v3
      with:
        name: jacoco-report
        path: target/site/jacoco
  
  security-scan:
    runs-on: ubuntu-latest
    needs: build-and-test
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: OWASP Dependency Check
      uses: dependency-check/Dependency-Check_Action@main
      with:
        project: 'My Project'
        path: '.'
        format: 'HTML'
        out: 'reports'
        args: >
          --failOnCVSS 7
          --enableRetired
    
    - name: Upload Dependency Check Report
      uses: actions/upload-artifact@v3
      with:
        name: dependency-check-report
        path: reports
  
  performance-test:
    runs-on: ubuntu-latest
    needs: build-and-test
    if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Run Gatling Tests
      run: mvn gatling:test -Dgatling.simulationClass=simulations.BasicSimulation
    
    - name: Upload Gatling Reports
      uses: actions/upload-artifact@v3
      with:
        name: gatling-reports
        path: target/gatling
  
  build-docker:
    runs-on: ubuntu-latest
    needs: [build-and-test, security-scan]
    if: github.ref == 'refs/heads/main'
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v2
    
    - name: Login to DockerHub
      uses: docker/login-action@v2
      with:
        username: ${{ secrets.DOCKERHUB_USERNAME }}
        password: ${{ secrets.DOCKERHUB_TOKEN }}
    
    - name: Build and Push
      uses: docker/build-push-action@v4
      with:
        context: .
        push: true
        tags: myorg/myapp:latest,myorg/myapp:${{ github.sha }}
        cache-from: type=registry,ref=myorg/myapp:buildcache
        cache-to: type=registry,ref=myorg/myapp:buildcache,mode=max
```

## Deployment and Operations

### Containerization Strategy

- **Dockerイメージ最適化**:
  - 多段ビルドによるイメージサイズの最小化
  - ベースイメージの選択（JDK版、JRE版、Alpine版）とトレードオフ
  - レイヤーキャッシュの効率的活用
  - セキュリティスキャン（Docker Scout, Trivy, Clair）の組み込み

- **コンテナオーケストレーション**:
  - Kubernetesリソース定義（Deployment, Service, ConfigMap, Secret）
  - Helmチャートによるパッケージング
  - ステートフルワークロードの管理（StatefulSet）
  - Operatorパターンの検討（複雑なアプリケーション向け）

- **イミュータブルインフラストラクチャ**:
  - 実行環境の一貫性確保
  - インフラストラクチャのバージョン管理（GitOps）
  - 自己修復性と水平スケーリングの自動化
  - コンテナライフサイクル管理（ヘルスチェック、グレースフルシャットダウン）

```yaml
# 最適化されたDockerfileの例
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
# 依存関係のみをコピーしてキャッシュレイヤーを活用
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests
# JAR内の依存関係を展開（実行時のレイヤー最適化）
RUN mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# 最終イメージは実行環境のみ
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# セキュリティ強化：rootユーザー以外で実行
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 展開されたレイヤーを順番にコピー（キャッシュ効率向上）
COPY --from=builder /app/target/extracted/dependencies/ ./
COPY --from=builder /app/target/extracted/spring-boot-loader/ ./
COPY --from=builder /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/target/extracted/application/ ./

# ヘルスチェックの定義
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
```

### Observability (Monitoring, Logging, Tracing)

- **構造化ロギング**:
  - JSON形式ログによるフィルタリングと検索効率の向上
  - MDC（Mapped Diagnostic Context）による相関ID伝播
  - ログレベルの適切な使い分け（DEBUG, INFO, WARN, ERROR）
  - 機密情報のマスキングとコンプライアンス対応

- **メトリクス収集**:
  - Micrometer統合によるアプリケーションメトリクス収集
  - Spring Boot Actuatorによるヘルスチェックと運用データ公開
  - カスタムメトリクスの定義（ビジネス指標、トランザクション指標）
  - アラートルールとダッシュボード設計

- **分散トレーシング**:
  - OpenTelemetryによる標準化されたトレース収集
  - サービス間通信の可視化（Zipkin, Jaeger, Tempo）
  - トレースコンテキスト伝播（HTTP headers, messaging）
  - パフォーマンスボトルネックの特定とトラブルシューティング

- **統合監視基盤**:
  - Prometheus & Grafanaによるメトリクス可視化
  - ELK Stack（Elasticsearch, Logstash, Kibana）またはGrafana Lokiによるログ管理
  - Tempo, Jaeger, Zipkinなどによるトレース分析
  - アラート一元管理とインシデント対応プロセス

```java
// 構造化ロギングとトレース連携の例
@Service
public class OrderServiceImpl implements OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);
    
    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final MeterRegistry meterRegistry;
    
    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository, 
                          PaymentClient paymentClient,
                          InventoryClient inventoryClient,
                          MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
        this.meterRegistry = meterRegistry;
        
        // カスタムメトリクスの登録
        Counter.builder("orders.created.total")
            .description("Total number of created orders")
            .tag("version", "v1")
            .register(meterRegistry);
    }
    
    @Override
    public OrderResult createOrder(OrderRequest request) {
        // 構造化ログ - 開始
        Map<String, Object> logContext = new HashMap<>();
        logContext.put("customer_id", request.getCustomerId());
        logContext.put("items_count", request.getItems().size());
        logContext.put("total_amount", calculateTotal(request.getItems()));
        
        logger.info("Order creation started: {}", JsonUtils.toJson(logContext));
        
        // スパン内のタイマー開始
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            // 在庫確認
            boolean stockAvailable = inventoryClient.checkAvailability(request.getItems());
            if (!stockAvailable) {
                logContext.put("error", "insufficient_stock");
                logger.warn("Order creation failed - insufficient stock: {}", 
                          JsonUtils.toJson(logContext));
                
                // エラーメトリクスのカウント
                meterRegistry.counter("orders.failed", 
                                    "reason", "insufficient_stock").increment();
                
                throw new InsufficientStockException("Product inventory is insufficient");
            }
            
            // 注文保存
            Order order = mapToOrder(request);
            Order savedOrder = orderRepository.save(order);
            
            // 支払い処理
            PaymentResult paymentResult = paymentClient.processPayment(
                    request.getPaymentDetails(), calculateTotal(request.getItems()));
            
            if (!paymentResult.isSuccessful()) {
                logContext.put("error", "payment_failed");
                logContext.put("error_code", paymentResult.getErrorCode());
                logger.error("Order creation failed - payment error: {}", 
                           JsonUtils.toJson(logContext));
                
                meterRegistry.counter("orders.failed", 
                                    "reason", "payment_failed").increment();
                
                throw new PaymentFailedException("決済処理に失敗しました: " + 
                                               paymentResult.getErrorMessage());
            }
            
            // Order completion processing and result return
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            savedOrder.setPaymentId(paymentResult.getTransactionId());
            orderRepository.save(savedOrder);
            
            // 成功メトリクスの記録
            meterRegistry.counter("orders.created.total").increment();
            
            OrderResult result = new OrderResult(savedOrder.getId(), 
                                               OrderStatus.CONFIRMED, 
                                               paymentResult.getTransactionId());
            
            // 構造化ログ - 完了
            logContext.put("order_id", savedOrder.getId());
            logContext.put("status", "confirmed");
            logger.info("Order creation completed successfully: {}", 
                      JsonUtils.toJson(logContext));
            
            // タイマー終了と記録
            timer.stop(Timer.builder("orders.creation.time")
                      .description("Time taken to create an order")
                      .tag("status", "success")
                      .register(meterRegistry));
            
            return result;
            
        } catch (Exception e) {
            // タイマー終了と記録（エラー）
            timer.stop(Timer.builder("orders.creation.time")
                      .description("Time taken to create an order")
                      .tag("status", "error")
                      .register(meterRegistry));
            
            logger.error("Unexpected error during order creation: " + e.getMessage(), e);
            throw e;
        }
    }
}
```

### CI/CDパイプライン

- **パイプライン設計原則**:
  - 段階的検証（ビルド→テスト→静的解析→セキュリティスキャン→デプロイ）
  - 環境分離（開発→テスト→ステージング→本番）
  - イミュータブルアーティファクト（同一バイナリの昇格）
  - 品質ゲートによる自動昇格・却下

- **デプロイメント戦略**:
  - ブルー/グリーンデプロイメント（ダウンタイムゼロ）
  - カナリアリリース（制限された利用者へのリリース）
  - A/Bテスト（機能の段階的展開）
  - フィーチャーフラグによる機能の選択的有効化

- **自動化とツール連携**:
  - GitHub Actions/GitLab CI/CircleCI/Jenkins/Azure DevOpsなどの活用
  - Terraform/Pulumi/AWS CDKなどによるIaCの自動適用
  - ChatOpsによるデプロイメントオーケストレーション
  - 障害検知と自動ロールバック機構

- **リリース管理**:
  - リリースノートの自動生成
  - 変更履歴の追跡と監査
  - バージョン管理（セマンティックバージョニング）
  - リリース承認ワークフロー

```yaml
# GitLab CIパイプラインの例（マイクロサービス向け）
stages:
  - build
  - test
  - analyze
  - package
  - deploy-dev
  - integration-test
  - deploy-staging
  - performance-test
  - deploy-prod

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"
  DOCKER_REGISTRY: "registry.example.com"
  SERVICE_NAME: "order-service"

cache:
  paths:
    - .m2/repository

build:
  stage: build
  image: maven:3.8-openjdk-17
  script:
    - mvn clean compile

unit-test:
  stage: test
  image: maven:3.8-openjdk-17
  script:
    - mvn test
  artifacts:
    paths:
      - target/surefire-reports
      - target/jacoco.exec

integration-test:
  stage: test
  image: maven:3.8-openjdk-17
  services:
    - name: postgres:14-alpine
      alias: postgres
    - name: redis:6-alpine
      alias: redis
  variables:
    POSTGRES_DB: testdb
    POSTGRES_USER: test
    POSTGRES_PASSWORD: test
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/testdb
    SPRING_REDIS_HOST: redis
  script:
    - mvn verify -DskipUnitTests

code-quality:
  stage: analyze
  image: maven:3.8-openjdk-17
  script:
    - mvn checkstyle:checkstyle pmd:pmd spotbugs:spotbugs
  artifacts:
    paths:
      - target/site

sonarqube:
  stage: analyze
  image: maven:3.8-openjdk-17
  script:
    - mvn sonar:sonar
      -Dsonar.host.url=$SONAR_URL
      -Dsonar.login=$SONAR_TOKEN
      -Dsonar.projectKey=$SERVICE_NAME
      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml

security-scan:
  stage: analyze
  image: maven:3.8-openjdk-17
  script:
    - mvn dependency-check:check
  artifacts:
    paths:
      - target/dependency-check-report.html

package-docker:
  stage: package
  image: docker:20
  services:
    - docker:20-dind
  script:
    - docker build -t $DOCKER_REGISTRY/$SERVICE_NAME:$CI_COMMIT_SHA .
    - docker push $DOCKER_REGISTRY/$SERVICE_NAME:$CI_COMMIT_SHA

deploy-dev:
  stage: deploy-dev
  image: bitnami/kubectl:latest
  environment:
    name: development
  script:
    - kubectl config use-context $KUBE_CONTEXT_DEV
    - envsubst < kubernetes/dev/deployment.yaml | kubectl apply -f -
    - kubectl rollout status deployment/$SERVICE_NAME

api-test:
  stage: integration-test
  image: postman/newman:alpine
  variables:
    API_URL: "https://dev-api.example.com"
  script:
    - newman run tests/postman/api-tests.json --environment tests/postman/dev-env.json

deploy-staging:
  stage: deploy-staging
  image: bitnami/kubectl:latest
  environment:
    name: staging
  when: manual
  script:
    - kubectl config use-context $KUBE_CONTEXT_STAGING
    - envsubst < kubernetes/staging/deployment.yaml | kubectl apply -f -
    - kubectl rollout status deployment/$SERVICE_NAME

performance-test:
  stage: performance-test
  image: gatling/gatling:latest
  script:
    - gatling.sh -sf tests/performance -s OrderApiSimulation -rf target/gatling-reports
  artifacts:
    paths:
      - target/gatling-reports

deploy-prod:
  stage: deploy-prod
  image: bitnami/kubectl:latest
  environment:
    name: production
  when: manual
  script:
    - kubectl config use-context $KUBE_CONTEXT_PROD
    - |
      # Blue-Greenデプロイメント
      # 現在のアクティブデプロイメントを確認
      ACTIVE_DEPLOY=$(kubectl get service/$SERVICE_NAME -o jsonpath='{.spec.selector.slot}')
      
      if [ "$ACTIVE_DEPLOY" == "blue" ]; then
        NEW_DEPLOY="green"
      else
        NEW_DEPLOY="blue"
      fi
      
      echo "Current active deployment: $ACTIVE_DEPLOY, deploying to: $NEW_DEPLOY"
      
      # 新しいデプロイメントをアップデート
      envsubst < kubernetes/prod/deployment-$NEW_DEPLOY.yaml | kubectl apply -f -
      kubectl rollout status deployment/$SERVICE_NAME-$NEW_DEPLOY
      
      # Health check
      echo "Verifying new deployment health..."
      kubectl exec deploy/$SERVICE_NAME-$NEW_DEPLOY -- curl -s http://localhost:8080/actuator/health
      
      # トラフィック切り替え
      echo "Switching traffic to $NEW_DEPLOY deployment"
      kubectl patch service/$SERVICE_NAME -p "{\"spec\":{\"selector\":{\"slot\":\"$NEW_DEPLOY\"}}}"
      
      # Old deployment can be deleted after a certain period as an option
```

### Disaster Recovery (DR) and Business Continuity Planning (BCP)

- **バックアップ戦略**:
  - データバックアップの種類と頻度（フル、増分、差分）
  - 自動化されたバックアップ検証（リストア演習）
  - バックアップの暗号化と保管（オフサイト保管）
  - 世代管理と保持ポリシー

- **災害復旧計画**:
  - 復旧時間目標（RTO）と復旧ポイント目標（RPO）の設定
  - フェイルオーバー手順とフォールバック計画
  - 障害シナリオ別の対応手順
  - 定期的な災害復旧訓練

- **高可用性設計**:
  - 地理的冗長性（マルチリージョン、マルチAZ配置）
  - アクティブ/アクティブまたはアクティブ/パッシブ構成
  - データレプリケーション戦略
  - グローバルロードバランシングとトラフィック分散

- **インシデント対応**:
  - エスカレーションプロセスとコミュニケーションプラン
  - 障害の検出と初動対応の自動化
  - 障害レポートと根本原因分析（RCA）
  - ポストモーテムと再発防止策

## Non-functional Requirements

In microservices architecture, non-functional requirements are as important as functional requirements. The following details the major non-functional requirements for enterprise Java applications.

### Performance Requirements

- **レスポンスタイム**:
  - APIエンドポイントの最大応答時間（例：95パーセンタイルで500ms以内）
  - バッチ処理の完了時間制約
  - ユーザーインターフェース操作の応答時間（例：ページロード2秒以内）
  - サービス間通信のタイムアウト設定（例：同期呼び出し1秒、非同期処理5秒）

- **スループット**:
  - ピーク時の毎秒トランザクション数（TPS）
  - 同時ユーザー数（並行セッション）
  - バルク処理の処理速度（例：1時間あたり100万レコード）
  - メッセージング処理能力（例：1秒あたり10,000メッセージ）

- **リソース使用効率**:
  - CPU使用率の上限（例：通常時70%以下、ピーク時90%以下）
  - メモリ使用量の制限と最適化（例：JVMヒープサイズ、GC設定）
  - ディスクI/O最適化（特にデータベース操作）
  - ネットワーク帯域幅の効率的利用

```java
// パフォーマンス最適化の例：N+1問題の解決
@Repository
public class OrderRepositoryImpl implements OrderRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // N+1問題が発生する実装
    public List<Order> findAllOrdersInefficient() {
        List<Order> orders = entityManager.createQuery(
            "SELECT o FROM Order o", Order.class).getResultList();
        
        // 各注文ごとに個別クエリが発生（N+1問題）
        for (Order order : orders) {
            order.getItems().size(); // 遅延ロードによる追加クエリ
        }
        
        return orders;
    }
    
    // 最適化された実装：JOIN FETCHによるN+1問題の解決
    public List<Order> findAllOrdersOptimized() {
        return entityManager.createQuery(
            "SELECT o FROM Order o LEFT JOIN FETCH o.items", Order.class)
            .getResultList();
    }
    
    // バッチサイズ最適化による別の解決法
    // @BatchSize(size=20)をOrderクラスに設定した場合
    public List<Order> findAllOrdersWithBatchSize() {
        List<Order> orders = entityManager.createQuery(
            "SELECT o FROM Order o", Order.class).getResultList();
        
        // バッチサイズによって20件ごとにまとめてロード
        for (Order order : orders) {
            order.getItems().size();
        }
        
        return orders;
    }
}
```

### Scalability Requirements

- **水平スケーラビリティ**:
  - 無停止でのインスタンス追加・削除
  - 自動スケーリングポリシー（CPU使用率、メモリ使用率、リクエスト数に基づく）
  - ステートレス設計による拡張性確保
  - キャッシュの分散と一貫性確保

- **垂直スケーラビリティ**:
  - リソース増強時の動作保証
  - スケールアップ時の性能向上比率の目標
  - 限界値の明確化

- **負荷分散**:
  - サービスディスカバリーと負荷分散戦略
  - セッションアフィニティ要件
  - ジオロケーションベースのルーティング
  - グローバル分散時のレイテンシ要件

### Availability Requirements

- **稼働率（SLA）**:
  - サービスレベル目標（例：99.95%の稼働率、月間ダウンタイム22分以内）
  - 計画メンテナンス時間の取り扱い
  - 各コンポーネントの可用性要件と全体SLAの関係

- **フォールトトレランス**:
  - 単一障害点（SPOF）の排除
  - サーキットブレーカーパターンの適用
  - デグラデーションモード（限定機能での継続運用）
  - リトライポリシーとバックオフ戦略

- **障害検知と復旧**:
  - ヘルスチェックの種類と頻度
  - 自動復旧メカニズム
  - フェイルオーバー時間制約
  - ディザスタリカバリ（DR）要件

```java
// サーキットブレーカーパターンの実装例（Resilience4j）
@Service
public class ExternalPaymentServiceClient {
    
    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    public ExternalPaymentServiceClient(RestTemplate restTemplate, 
                                       CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restTemplate = restTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }
    
    @Autowired
    public void configureCircuitBreaker() {
        // サーキットブレーカー設定
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)             // 50%以上の失敗率で開路
            .waitDurationInOpenState(Duration.ofSeconds(30)) // 開路状態を30秒維持
            .permittedNumberOfCallsInHalfOpenState(10)      // 半開状態で10回の試行
            .slidingWindowSize(100)                         // 直近100回の呼び出しを評価
            .recordExceptions(HttpServerErrorException.class,
                            HttpClientErrorException.class,
                            ConnectException.class,
                            SocketTimeoutException.class)   // 障害とみなす例外
            .build();
            
        circuitBreakerRegistry.addConfiguration("paymentService", circuitBreakerConfig);
    }
    
    public PaymentResult processPayment(PaymentRequest request) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        
        // サーキットブレーカーで外部サービス呼び出しを保護
        Supplier<PaymentResult> decoratedSupplier = CircuitBreaker
            .decorateSupplier(circuitBreaker, () -> callPaymentService(request));
            
        try {
            // 実行（サーキットブレーカーによる保護あり）
            return decoratedSupplier.get();
        } catch (CircuitBreakerOpenException e) {
            // サーキットブレーカーが開いている場合のフォールバック
            log.warn("Circuit breaker is open, falling back to alternative payment processor");
            return processPaymentWithFallback(request);
        } catch (Exception e) {
            // その他の例外処理
            log.error("Payment processing failed", e);
            throw new PaymentProcessingException("決済処理に失敗しました", e);
        }
    }
    
    private PaymentResult callPaymentService(PaymentRequest request) {
        // 実際の外部サービス呼び出し
        return restTemplate.postForObject("/api/payments", request, PaymentResult.class);
    }
    
    private PaymentResult processPaymentWithFallback(PaymentRequest request) {
        // 代替の決済処理またはオフライン処理キューへの登録
        return new PaymentResult(false, null, "CIRCUIT_OPEN", 
                               "現在決済サービスが利用できません。後ほど再試行します。");
    }
}
```

### Security Requirements

- **認証・認可**:
  - 認証方式（例：OpenID Connect, SAML, JWT）
  - 多要素認証（MFA）要件
  - ロールベース/属性ベースのアクセス制御（RBAC/ABAC）
  - セッション管理ポリシー（タイムアウト、同時セッション数）

- **データ保護**:
  - 保存データの暗号化要件（アルゴリズム、鍵管理）
  - 転送中データの暗号化（TLS 1.3, mTLS）
  - 個人情報保護対策（匿名化、仮名化）
  - 機密データの取り扱いポリシー

- **監査とコンプライアンス**:
  - 監査ログの要件（何を、どのように、どれだけの期間）
  - 法規制対応（GDPR, PCI DSS, HIPAA等）
  - 脆弱性管理プロセス
  - ペネトレーションテスト頻度と範囲

- **侵入検知と防御**:
  - 入力検証ポリシー
  - OWASPトップ10対策
  - APIセキュリティ（レート制限、トークン検証）
  - マルウェア/ウイルス対策

### Operations and Maintainability Requirements

- **監視・観測性**:
  - 必須モニタリング指標と閾値
  - アラート設定と通知ルート
  - ロギング要件（フォーマット、詳細度、保持期間）
  - 分散トレーシング要件

- **デプロイメント**:
  - デプロイ頻度と所要時間
  - ゼロダウンタイムデプロイ要件
  - ロールバック要件と復旧時間
  - カナリアリリース、フィーチャーフラグ要件

- **構成管理**:
  - 環境間での設定差異管理
  - シークレット管理
  - 構成変更の追跡と監査
  - A/Bテスト機能

- **バックアップと復元**:
  - バックアップ種類と頻度
  - 復元時間目標（RTO）
  - 復元ポイント目標（RPO）
  - バックアップ検証プロセス

### Data Management Requirements

- **データ永続性**:
  - データ保持ポリシー
  - バックアップと復元の要件
  - アーカイブ戦略
  - データ冗長性レベル

- **データ整合性**:
  - トランザクション境界と分離レベル
  - 最終的整合性の許容範囲
  - イベント整合性の保証
  - 楽観的/悲観的ロックの要件

- **データボリューム**:
  - 想定データ量と成長率
  - クエリパフォーマンス要件
  - データパーティショニング戦略
  - コールドデータ/ホットデータの区分管理

- **データ品質**:
  - データ検証ルール
  - マスターデータ管理
  - データクレンジング要件
  - データ移行・変換要件

### Usability Requirements

- **ユーザーインターフェース**:
  - レスポンシブデザイン要件
  - アクセシビリティ基準（WCAG準拠レベル）
  - 多言語対応
  - 異なるデバイスでの動作保証

- **ユーザー体験**:
  - エラーメッセージの親切さ
  - 操作ステップ数の制限
  - ヘルプ・ガイダンス機能
  - 一貫性のあるUI/UX

### Localization Requirements

- **国際化対応**:
  - サポートする言語・地域
  - 文字セット・エンコーディング
  - タイムゾーン処理
  - 数値・日付・通貨のフォーマット

- **地域固有の要件**:
  - 法的規制対応
  - 文化的配慮
  - 地域別のビジネスルール
  - 地域別のデータ保持ポリシー

## Best Practices

### Coding Standards

- Google Java Style Guideなどの標準的な規約の採用
- Checkstyle, PMD, SpotBugsなどの静的解析ツールの活用
- コードレビュープロセスの確立

### ドキュメンテーション

- **APIドキュメント**:
  - Swagger/OpenAPIによるREST API仕様
  - JavadocによるJava API文書化
  - APIの使用例とサンプルコード
  - 利用者ガイドとチュートリアル

- **アーキテクチャドキュメント**:
  - C4モデルによる多層的なアーキテクチャ文書（コンテキスト、コンテナ、コンポーネント、コード）
  - ADR（Architecture Decision Records）による意思決定の記録
  - システムコンテキスト図とデータフロー図
  - 非機能要件のマッピングと検証方法

- **運用ドキュメント**:
  - 環境構築手順書
  - デプロイメントプロセス
  - 監視設定とアラート対応
  - インシデント対応手順

- **開発プロセス文書**:
  - コーディング規約
  - レビュープロセス
  - ブランチ戦略とリリースフロー
  - テスト戦略と品質基準

```java
/**
 * 注文サービスのインターフェース。
 * <p>
 * このサービスは注文のライフサイクル全体を管理し、以下の機能を提供します：
 * <ul>
 *   <li>注文の作成と検証</li>
 *   <li>支払い処理の統合</li>
 *   <li>在庫確認と予約</li>
 *   <li>注文状態の追跡</li>
 * </ul>
 * </p>
 * 
 * @author 開発チーム
 * @version 1.0
 * @since 2025-01-01
 */
public interface OrderService {
    
    /**
     * 新しい注文を作成します。
     * <p>
     * このメソッドは以下のステップを実行します：
     * <ol>
     *   <li>注文リクエストの検証</li>
     *   <li>在庫の確認</li>
     *   <li>支払い処理</li>
     *   <li>注文の永続化</li>
     * </ol>
     * </p>
     *
     * @param request 注文作成リクエスト（必須）
     * @return 作成された注文の結果
     * @throws ValidationException 注文リクエストが無効な場合
     * @throws InsufficientStockException 在庫が不足している場合
     * @throws PaymentFailedException 支払い処理に失敗した場合
     */
    OrderResult createOrder(OrderRequest request);
    
    /**
     * 注文IDに基づいて注文を取得します。
     *
     * @param orderId 取得する注文のID
     * @return 見つかった注文、存在しない場合は例外をスロー
     * @throws ResourceNotFoundException 指定されたIDの注文が存在しない場合
     */
    Order getOrderById(Long orderId);
    
    /**
     * 顧客IDに基づいて注文履歴を取得します。
     * <p>
     * 結果はページング処理され、指定された条件でソートされます。
     * </p>
     *
     * @param customerId 顧客ID
     * @param pageable ページングとソートの情報
     * @return 注文のページング済みリスト
     */
    Page<Order> getOrdersByCustomerId(Long customerId, Pageable pageable);
    
    /**
     * 注文をキャンセルします。
     * <p>
     * 注文のキャンセルは、注文が「処理中」または「確認済み」状態の場合のみ可能です。
     * キャンセル処理には以下が含まれます：
     * <ul>
     *   <li>注文状態の更新</li>
     *   <li>在庫の解放</li>
     *   <li>該当する場合の払い戻し処理</li>
     * </ul>
     * </p>
     *
     * @param orderId キャンセルする注文のID
     * @param reason キャンセル理由（オプション）
     * @return キャンセル処理の結果
     * @throws ResourceNotFoundException 指定されたIDの注文が存在しない場合
     * @throws InvalidStateException 注文が既に処理済みまたは発送済みの場合
     */
    CancellationResult cancelOrder(Long orderId, String reason);
}
```

### Version Control

- **セマンティックバージョニング**:
  - メジャー.マイナー.パッチ形式の採用
  - 後方互換性のないAPI変更時のメジャーバージョン更新
  - APIの拡張時のマイナーバージョン更新
  - バグ修正時のパッチバージョン更新

- **ブランチ戦略**:
  - トランクベース開発またはGitFlow/GitHub Flowの採用
  - フィーチャーブランチの短命化
  - プルリクエスト/マージリクエストのレビュープロセス
  - 継続的インテグレーションによる早期問題検出

- **変更履歴管理**:
  - CHANGELOGファイルの維持
  - リリースノートの自動生成
  - APIバージョン管理と互換性保証
  - 非推奨化プロセスと移行期間の設定

### Team Development Practices

- **アジャイル開発手法**:
  - スクラム/カンバンプロセスの採用
  - 短いイテレーションサイクル
  - 定期的なレトロスペクティブと改善
  - 透明性の高いタスク管理

- **ペアプログラミングとモブプログラミング**:
  - 知識共有と品質向上
  - コードオーナーシップの分散
  - 新メンバーのオンボーディング加速
  - 複雑な問題への共同取り組み

- **コードレビュー文化**:
  - 建設的なフィードバックの促進
  - 自動化されたコードスタイルチェック
  - レビュー基準の明確化
  - コードレビューの効率化（プレレビューツール等）

- **技術的負債の管理**:
  - 技術負債の可視化と計測
  - 定期的なリファクタリング時間の確保
  - 「ボーイスカウトルール」の適用
  - リファクタリングとビジネス価値のバランス

### Cloud-Native Practices

- **インフラストラクチャ・アズ・コード（IaC）**:
  - Terraform, AWS CloudFormation, Azure ARMテンプレート等の活用
  - 環境のバージョン管理とリプロダクト
  - イミュータブルインフラストラクチャの実現
  - 複数環境の一貫性確保

- **コンテナオーケストレーション**:
  - Kubernetesベストプラクティス
  - ヘルムチャートによるアプリケーションパッケージング
  - Appropriate management of stateful services
  - Integration with cloud resources (CSI, CNI, etc.)

- **Serverless architecture**:
  - FaaS utilization for appropriate use cases
  - Cold start countermeasures
  - Cost optimization
  - Monitoring and failure detection

- **Effective utilization of cloud services**:
  - Strategic adoption of managed services
  - Application of cloud-native patterns
  - Multi-cloud/hybrid cloud strategies
  - Cloud cost optimization

## Summary

Building enterprise Java application microservices architecture requires a comprehensive approach that goes beyond mere technology selection. This guide has covered the key considerations for building modern enterprise systems, from architecture design to development methodologies, performance optimization, security measures, testing strategies, deployment, operations, and non-functional requirements.

When properly designed and implemented, microservices architecture can significantly improve business agility, scalability, and resilience. However, its complexity should not be underestimated. To address challenges such as distributed system issues, ensuring reliability of inter-service communication, maintaining data consistency, and operational complexity, it is important to adapt the practices introduced in this guide to your organization's context.

Successful microservices implementation requires not only technical aspects but also transformation of organizational structure, development culture, and operational processes. As Conway's Law suggests, system architecture tends to reflect organizational structure, so team composition and communication patterns significantly influence the success of microservices adoption.

Finally, while this guide presents best practices and considerations, there is no "silver bullet" that can be uniformly applied to all projects. It is important to build sustainable, value-creating architectures while making appropriate trade-offs, taking into account each organization's business requirements, existing systems, team skill sets, and resource constraints.
