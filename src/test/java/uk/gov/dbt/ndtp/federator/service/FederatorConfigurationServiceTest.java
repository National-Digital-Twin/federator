package uk.gov.dbt.ndtp.federator.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.dbt.ndtp.federator.converter.ConfigurationConverter;
import uk.gov.dbt.ndtp.federator.exceptions.ManagementNodeException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.*;
import uk.gov.dbt.ndtp.federator.model.dto.*;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit test class for FederatorConfigurationService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatorConfigurationServiceTest {

    @Mock
    private ManagementNodeDataHandler mockManagementNodeDataHandler;

    @Mock
    private JwtTokenService mockJwtTokenService;

    @Mock
    private InMemoryConfigurationStore mockConfigurationStore;

    private FederatorConfigurationService service;

    @BeforeEach
    void setUp() {
        // Setup will be done in individual tests to avoid unnecessary stubbing
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should initialize service with cache enabled")
    void testServiceInitialization() {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        when(mockJwtTokenService.getTokenInfo()).thenReturn(createValidTokenInfo());

        // Act
        service = createServiceWithMocks(true, false);

        // Assert
        assertNotNull(service);
        FederatorConfigurationService.FederatorServiceStatistics stats = service.getServiceStatistics();
        assertTrue(stats.isCacheEnabled());
        assertFalse(stats.isAutoRefreshEnabled());
    }

    @Test
    @Order(2)
    @DisplayName("Should retrieve producer configurations from cache when available")
    void testGetProducerConfigurations_FromCache() throws ManagementNodeException {
        // Arrange
        List<ProducerConfiguration> cachedConfigs = createSampleProducerConfigurations();
        when(mockConfigurationStore.getAllProducerConfigurations()).thenReturn(cachedConfigs);

        service = createServiceWithMocks(true, false);

        // Act
        List<ProducerConfiguration> result = service.getProducerConfigurations();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BCC-PRODUCER-1", result.get(0).getProducerId());

        // Verify cache was checked but management node was not called
        verify(mockConfigurationStore, times(1)).getAllProducerConfigurations();
        verify(mockManagementNodeDataHandler, never()).getProducerData(anyString());

        // Check statistics
        FederatorConfigurationService.FederatorServiceStatistics stats = service.getServiceStatistics();
        assertEquals(1, stats.getCacheHits());
        assertEquals(0, stats.getCacheMisses());
    }

    @Test
    @Order(3)
    @DisplayName("Should fetch producer configurations from management node on cache miss")
    void testGetProducerConfigurations_CacheMiss() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        List<ProducerConfiguration> emptyCache = new ArrayList<>();
        List<ProducerConfiguration> fetchedConfigs = createSampleProducerConfigurations();

        when(mockConfigurationStore.getAllProducerConfigurations()).thenReturn(emptyCache);
        when(mockManagementNodeDataHandler.getProducerData(anyString())).thenReturn(fetchedConfigs);

        service = createServiceWithMocks(true, false);

        // Act
        List<ProducerConfiguration> result = service.getProducerConfigurations();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        // Verify both cache and management node were called
        verify(mockConfigurationStore, times(1)).getAllProducerConfigurations();
        verify(mockManagementNodeDataHandler, times(1)).getProducerData("test-jwt-token");
        verify(mockConfigurationStore, times(1)).storeProducerConfigurations(fetchedConfigs);
    }

    @Test
    @Order(4)
    @DisplayName("Should retrieve consumer configurations from cache")
    void testGetConsumerConfigurations_FromCache() throws ManagementNodeException {
        // Arrange
        List<ConsumerConfiguration> cachedConfigs = createSampleConsumerConfigurations();
        when(mockConfigurationStore.getAllConsumerConfigurations()).thenReturn(cachedConfigs);

        service = createServiceWithMocks(true, false);

        // Act
        List<ConsumerConfiguration> result = service.getConsumerConfigurations();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("FEDERATOR_HEG", result.get(0).getClientId());

        // Verify cache was used
        verify(mockConfigurationStore, times(1)).getAllConsumerConfigurations();
        verify(mockManagementNodeDataHandler, never()).getConsumerData(anyString());
    }

    @Test
    @Order(5)
    @DisplayName("Should get specific producer configuration by ID")
    void testGetProducerConfiguration_ById() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        String producerId = "BCC-PRODUCER-1";
        ProducerConfiguration expectedConfig = createSampleProducerConfiguration();

        when(mockConfigurationStore.getProducerConfiguration(producerId)).thenReturn(Optional.empty());
        when(mockManagementNodeDataHandler.getProducerConfigurationByProducerId(anyString(), eq(producerId)))
                .thenReturn(expectedConfig);

        service = createServiceWithMocks(true, false);

        // Act
        ProducerConfiguration result = service.getProducerConfiguration(producerId);

        // Assert
        assertNotNull(result);
        assertEquals(producerId, result.getProducerId());
        verify(mockManagementNodeDataHandler, times(1))
                .getProducerConfigurationByProducerId("test-jwt-token", producerId);
    }

    @Test
    @Order(6)
    @DisplayName("Should get specific consumer configuration by client ID")
    void testGetConsumerConfiguration_ByClientId() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        String clientId = "FEDERATOR_HEG";
        ConsumerConfiguration expectedConfig = createSampleConsumerConfiguration();

        when(mockConfigurationStore.getConsumerConfiguration(clientId)).thenReturn(Optional.empty());
        when(mockManagementNodeDataHandler.getConsumerConfigurationByClientId(anyString(), eq(clientId)))
                .thenReturn(expectedConfig);

        service = createServiceWithMocks(true, false);

        // Act
        ConsumerConfiguration result = service.getConsumerConfiguration(clientId);

        // Assert
        assertNotNull(result);
        assertEquals(clientId, result.getClientId());
        verify(mockManagementNodeDataHandler, times(1))
                .getConsumerConfigurationByClientId("test-jwt-token", clientId);
    }

    @Test
    @Order(7)
    @DisplayName("Should get ProducerDTO directly")
    void testGetProducerDTO() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        String producerId = "BCC-PRODUCER-1";
        ProducerDTO expectedDTO = createSampleProducerDTO();

        when(mockManagementNodeDataHandler.getProducerDataByProducerId(anyString(), eq(producerId)))
                .thenReturn(expectedDTO);

        service = createServiceWithMocks(true, false);

        // Act
        ProducerDTO result = service.getProducerDTO(producerId);

        // Assert
        assertNotNull(result);
        assertEquals("BCC-PRODUCER-1", result.getName());
        verify(mockManagementNodeDataHandler, times(1))
                .getProducerDataByProducerId("test-jwt-token", producerId);
    }

    @Test
    @Order(8)
    @DisplayName("Should get ConsumerDTO directly")
    void testGetConsumerDTO() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        String clientId = "FEDERATOR_HEG";
        ConsumerDTO expectedDTO = createSampleConsumerDTO();

        when(mockManagementNodeDataHandler.getConsumerDataByClientId(anyString(), eq(clientId)))
                .thenReturn(expectedDTO);

        service = createServiceWithMocks(true, false);

        // Act
        ConsumerDTO result = service.getConsumerDTO(clientId);

        // Assert
        assertNotNull(result);
        assertEquals("HEG-CONSUMER-1", result.getName());
        verify(mockManagementNodeDataHandler, times(1))
                .getConsumerDataByClientId("test-jwt-token", clientId);
    }

    @Test
    @Order(9)
    @DisplayName("Should get ProducerConfigurationResponse")
    void testGetProducerConfigurationResponse() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        String producerId = "BCC-PRODUCER-1";
        ManagementNodeDataHandler.ProducerConfigurationResponse expectedResponse =
                createSampleProducerConfigurationResponse();

        when(mockManagementNodeDataHandler.getProducerConfigurationResponse(anyString(), eq(producerId)))
                .thenReturn(expectedResponse);

        service = createServiceWithMocks(true, false);

        // Act
        ManagementNodeDataHandler.ProducerConfigurationResponse result =
                service.getProducerConfigurationResponse(producerId);

        // Assert
        assertNotNull(result);
        assertEquals("FEDERATOR_BCC", result.getClientId());
        assertEquals(1, result.getProducers().size());
    }

    @Test
    @Order(10)
    @DisplayName("Should check topic access for a client")
    void testHasTopicAccess() throws ManagementNodeException {
        // Arrange
        String clientId = "FEDERATOR_HEG";
        String topicName = "topic.PendingPlanningApplications";
        ConsumerConfiguration config = createSampleConsumerConfiguration();

        // Create Topic using the model package Topic class
        Topic topic = new Topic();
        topic.setName(topicName);
        topic.setDescription("Pending Planning Applications");
        topic.setActive(true);

        List<Topic> topics = new ArrayList<>();
        topics.add(topic);
        config.setTopics(topics);

        when(mockConfigurationStore.getConsumerConfiguration(clientId))
                .thenReturn(Optional.of(config));

        service = createServiceWithMocks(true, false);

        // Act
        boolean hasAccess = service.hasTopicAccess(clientId, topicName);

        // Assert
        assertTrue(hasAccess);

        // Test no access case
        boolean noAccess = service.hasTopicAccess(clientId, "non.existent.topic");
        assertFalse(noAccess);
    }

    @Test
    @Order(11)
    @DisplayName("Should check if client API is valid")
    void testIsClientApiValid() throws ManagementNodeException {
        // Arrange
        String clientId = "FEDERATOR_HEG";
        ConsumerConfiguration config = createSampleConsumerConfiguration();
        ApiConfiguration api = new ApiConfiguration();
        api.setRevoked(false);
        config.setApi(api);

        when(mockConfigurationStore.getConsumerConfiguration(clientId))
                .thenReturn(Optional.of(config));

        service = createServiceWithMocks(true, false);

        // Act
        boolean isValid = service.isClientApiValid(clientId);

        // Assert
        assertTrue(isValid);
    }

    @Test
    @Order(12)
    @DisplayName("Should perform synchronous configuration refresh")
    void testRefreshConfigurationsSync() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        when(mockJwtTokenService.getTokenInfo()).thenReturn(createValidTokenInfo());
        List<ProducerConfiguration> producers = createSampleProducerConfigurations();
        List<ConsumerConfiguration> consumers = createSampleConsumerConfigurations();

        // Setup the mocks to always return success (handle retries)
        when(mockManagementNodeDataHandler.getProducerData(anyString())).thenReturn(producers);
        when(mockManagementNodeDataHandler.getConsumerData(anyString())).thenReturn(consumers);

        // Mock the health check that might be called during refresh
        when(mockManagementNodeDataHandler.isHealthy()).thenReturn(true);

        service = createServiceWithMocks(true, false);

        // Act
        FederatorConfigurationService.RefreshResult result = service.refreshConfigurationsSync();

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccess(), "Refresh should be successful");
        assertEquals(1, result.getProducerCount());
        assertEquals(1, result.getConsumerCount());
        assertNotNull(result.getRefreshTime());

        verify(mockConfigurationStore, atLeastOnce()).storeProducerConfigurations(producers);
        verify(mockConfigurationStore, atLeastOnce()).storeConsumerConfigurations(consumers);
    }

    @Test
    @Order(13)
    @DisplayName("Should perform asynchronous configuration refresh")
    void testRefreshConfigurationsAsync() throws Exception {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        List<ProducerConfiguration> producers = createSampleProducerConfigurations();
        List<ConsumerConfiguration> consumers = createSampleConsumerConfigurations();

        // Setup async mocks - these need to return CompletableFutures
        when(mockManagementNodeDataHandler.getProducerDataAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(producers));
        when(mockManagementNodeDataHandler.getConsumerDataAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(consumers));

        // Also setup sync mocks as fallback
        when(mockManagementNodeDataHandler.getProducerData(anyString())).thenReturn(producers);
        when(mockManagementNodeDataHandler.getConsumerData(anyString())).thenReturn(consumers);

        service = createServiceWithMocks(true, false);

        // Act
        CompletableFuture<FederatorConfigurationService.RefreshResult> future =
                service.refreshConfigurationsAsync();
        FederatorConfigurationService.RefreshResult result = future.get(10, TimeUnit.SECONDS);

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getProducerCount());
        assertEquals(1, result.getConsumerCount());
    }

    @Test
    @Order(14)
    @DisplayName("Should handle circuit breaker opening on failures")
    void testCircuitBreakerOpensOnFailure() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        when(mockJwtTokenService.getTokenInfo()).thenReturn(createValidTokenInfo());
        when(mockConfigurationStore.getAllProducerConfigurations()).thenReturn(new ArrayList<>());

        // Mock both sync and async methods to throw exception
        ManagementNodeException exception = new ManagementNodeException("Connection failed");
        when(mockManagementNodeDataHandler.getProducerData(anyString()))
                .thenThrow(exception);
        when(mockManagementNodeDataHandler.getProducerDataAsync(anyString()))
                .thenReturn(CompletableFuture.failedFuture(exception));

        service = createServiceWithMocks(true, false);

        // Act & Assert
        assertThrows(ManagementNodeException.class, () -> service.getProducerConfigurations());

        // Verify circuit breaker state
        FederatorConfigurationService.FederatorServiceStatistics stats = service.getServiceStatistics();
        assertEquals(FederatorConfigurationService.CircuitBreakerState.OPEN,
                stats.getCircuitBreakerState());

        // Next call should fail immediately due to open circuit
        assertThrows(ManagementNodeException.class, () -> service.getProducerConfigurations());

        // Verify management node was only called once (circuit breaker prevents second call)
        verify(mockManagementNodeDataHandler, times(1)).getProducerData(anyString());
    }

    @Test
    @Order(15)
    @DisplayName("Should perform health check successfully")
    void testPerformHealthCheck() {
        // Arrange
        when(mockJwtTokenService.getTokenInfo()).thenReturn(createValidTokenInfo());
        when(mockManagementNodeDataHandler.isHealthy()).thenReturn(true);
        when(mockConfigurationStore.getCacheStatistics()).thenReturn(createCacheStatistics());

        service = createServiceWithMocks(true, false);

        // Act
        FederatorConfigurationService.HealthCheckResult result = service.performHealthCheck();

        // Assert
        assertNotNull(result);
        assertTrue(result.isHealthy());
        assertNotNull(result.getCheckTime());
        assertNotNull(result.getDetails());
        assertEquals("HEALTHY", result.getDetails().get("managementNode"));
        assertEquals("VALID", result.getDetails().get("jwtToken"));
        assertEquals("CLOSED", result.getDetails().get("circuitBreaker"));
    }

    @Test
    @Order(16)
    @DisplayName("Should get comprehensive service statistics")
    void testGetServiceStatistics() throws ManagementNodeException {
        // Arrange
        when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        when(mockJwtTokenService.getTokenInfo()).thenReturn(createValidTokenInfo());
        when(mockConfigurationStore.getAllProducerConfigurations())
                .thenReturn(new ArrayList<>())
                .thenReturn(createSampleProducerConfigurations());
        when(mockManagementNodeDataHandler.getProducerData(anyString()))
                .thenReturn(createSampleProducerConfigurations());
        when(mockConfigurationStore.getCacheStatistics()).thenReturn(createCacheStatistics());
        when(mockManagementNodeDataHandler.getStatistics()).thenReturn(createManagementNodeStatistics());

        service = createServiceWithMocks(true, false);

        // Trigger some operations
        service.getProducerConfigurations(); // Cache miss
        service.getProducerConfigurations(); // Cache hit

        // Act
        FederatorConfigurationService.FederatorServiceStatistics stats = service.getServiceStatistics();

        // Assert
        assertNotNull(stats);
        assertEquals(2, stats.getConfigurationRequests());
        assertEquals(1, stats.getCacheHits());
        assertEquals(1, stats.getCacheMisses());
        assertEquals(50.0, stats.getCacheHitRate(), 0.01);
        assertTrue(stats.isCacheEnabled());
        assertNotNull(stats.getCacheStatistics());
        assertNotNull(stats.getManagementNodeStatistics());
        assertNotNull(stats.getTokenInfo());
    }

    @Test
    @Order(17)
    @DisplayName("Should get client filter class name")
    void testGetClientFilterClassName() throws ManagementNodeException {
        // Arrange
        String clientId = "FEDERATOR_HEG";
        ConsumerConfiguration config = createSampleConsumerConfiguration();
        config.setFilterClassname("com.example.CustomFilter");

        when(mockConfigurationStore.getConsumerConfiguration(clientId))
                .thenReturn(Optional.of(config));

        service = createServiceWithMocks(true, false);

        // Act
        String filterClassName = service.getClientFilterClassName(clientId);

        // Assert
        assertEquals("com.example.CustomFilter", filterClassName);
    }

    @Test
    @Order(18)
    @DisplayName("Should get client Kafka configuration")
    void testGetClientKafkaConfiguration() throws ManagementNodeException {
        // Arrange
        String clientId = "FEDERATOR_HEG";
        ConsumerConfiguration config = createSampleConsumerConfiguration();
        KafkaConfiguration kafkaConfig = new KafkaConfiguration();
        kafkaConfig.setBootstrapServers("localhost:9092");
        kafkaConfig.setGroupId("test-group");
        config.setKafka(kafkaConfig);

        when(mockConfigurationStore.getConsumerConfiguration(clientId))
                .thenReturn(Optional.of(config));

        service = createServiceWithMocks(true, false);

        // Act
        KafkaConfiguration result = service.getClientKafkaConfiguration(clientId);

        // Assert
        assertNotNull(result);
        assertEquals("localhost:9092", result.getBootstrapServers());
        assertEquals("test-group", result.getGroupId());
    }

    @Test
    @Order(19)
    @DisplayName("Should get client Security configuration")
    void testGetClientSecurityConfiguration() throws ManagementNodeException {
        // Arrange
        String clientId = "FEDERATOR_HEG";
        ConsumerConfiguration config = createSampleConsumerConfiguration();
        SecurityConfiguration securityConfig = new SecurityConfiguration();
        securityConfig.setProtocol("SASL_SSL");
        securityConfig.setSaslMechanism("PLAIN");
        config.setSecurity(securityConfig);

        when(mockConfigurationStore.getConsumerConfiguration(clientId))
                .thenReturn(Optional.of(config));

        service = createServiceWithMocks(true, false);

        // Act
        SecurityConfiguration result = service.getClientSecurityConfiguration(clientId);

        // Assert
        assertNotNull(result);
        assertEquals("SASL_SSL", result.getProtocol());
        assertEquals("PLAIN", result.getSaslMechanism());
    }

    @Test
    @Order(20)
    @DisplayName("Should handle service shutdown gracefully")
    void testServiceShutdown() {
        // Arrange
        service = createServiceWithMocks(true, false);

        // Act
        service.close();

        // Assert
        verify(mockManagementNodeDataHandler, times(1)).close();
        verify(mockJwtTokenService, times(1)).close();
    }

    // Helper methods

    private FederatorConfigurationService createServiceWithMocks(boolean cacheEnabled, boolean autoRefreshEnabled) {
        // Ensure JWT token is always available
        lenient().when(mockJwtTokenService.getCurrentToken()).thenReturn("test-jwt-token");
        lenient().when(mockJwtTokenService.getTokenInfo()).thenReturn(createValidTokenInfo());

        return new FederatorConfigurationService(
                mockManagementNodeDataHandler,
                mockJwtTokenService,
                mockConfigurationStore,
                cacheEnabled,
                autoRefreshEnabled,
                3600000L,  // 1 hour refresh interval
                7200000L,  // 2 hours cache TTL
                false      // parallel refresh disabled - important for sync behavior
        );
    }

    private List<ProducerConfiguration> createSampleProducerConfigurations() {
        return Arrays.asList(createSampleProducerConfiguration());
    }

    private ProducerConfiguration createSampleProducerConfiguration() {
        ProducerConfiguration config = new ProducerConfiguration();
        config.setProducerId("BCC-PRODUCER-1");
        config.setName("BCC-PRODUCER-1");
        config.setDescription("BCC Producer 1");
        config.setIdpClientId("FEDERATOR_BCC");
        config.setActive(true);
        config.setHost("https://heg.gov.uk");
        config.setPort(443);
        config.setTls(true);

        // Create DataProviders using the correct inner class structure
        ProducerConfiguration.DataProvider dataProvider = ProducerConfiguration.DataProvider.builder()
                .name("PendingPlanningApplications")
                .topic("topic.PendingPlanningApplications")
                .active(true)
                .build();

        ProducerConfiguration.ConsumerInfo consumerInfo = ProducerConfiguration.ConsumerInfo.builder()
                .name("HEG-CONSUMER-1")
                .idpClientId("FEDERATOR_HEG")
                .build();

        List<ProducerConfiguration.ConsumerInfo> consumers = new ArrayList<>();
        consumers.add(consumerInfo);
        dataProvider.setConsumers(consumers);

        List<ProducerConfiguration.DataProvider> dataProviders = new ArrayList<>();
        dataProviders.add(dataProvider);
        config.setDataProviders(dataProviders);

        return config;
    }

    private List<ConsumerConfiguration> createSampleConsumerConfigurations() {
        return Arrays.asList(createSampleConsumerConfiguration());
    }

    private ConsumerConfiguration createSampleConsumerConfiguration() {
        ConsumerConfiguration config = new ConsumerConfiguration();
        config.setClientId("FEDERATOR_HEG");
        config.setName("HEG-CONSUMER-1");
        config.setActive(true);
        config.setFilterClassname("com.example.CustomFilter");
        config.setTopics(new ArrayList<>());
        config.setApi(new ApiConfiguration());
        config.setKafka(new KafkaConfiguration());
        config.setSecurity(new SecurityConfiguration());
        return config;
    }

    private ProducerDTO createSampleProducerDTO() {
        ProducerDTO producer = new ProducerDTO();
        producer.setName("BCC-PRODUCER-1");
        producer.setDescription("BCC Producer 1");
        producer.setActive(true);
        producer.setHost("https://heg.gov.uk");
        producer.setPort(new BigDecimal(443));
        producer.setTls(true);
        producer.setIdpClientId("FEDERATOR_BCC");

        DataProviderDTO dataProvider = new DataProviderDTO();
        dataProvider.setName("PendingPlanningApplications");
        dataProvider.setTopic("topic.PendingPlanningApplications");
        dataProvider.setActive(true);

        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setName("HEG-CONSUMER-1");
        consumer.setIdpClientId("FEDERATOR_HEG");
        dataProvider.setConsumers(Arrays.asList(consumer));

        producer.setDataProviders(Arrays.asList(dataProvider));
        return producer;
    }

    private ConsumerDTO createSampleConsumerDTO() {
        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setName("HEG-CONSUMER-1");
        consumer.setIdpClientId("FEDERATOR_HEG");
        return consumer;
    }

    private ManagementNodeDataHandler.ProducerConfigurationResponse createSampleProducerConfigurationResponse() {
        // Create the response using the inner class from ManagementNodeDataHandler
        ManagementNodeDataHandler.ProducerConfigurationResponse response =
                new ManagementNodeDataHandler.ProducerConfigurationResponse();
        response.setClientId("FEDERATOR_BCC");
        response.setProducers(Arrays.asList(createSampleProducerDTO()));
        return response;
    }

    private JwtTokenService.JwtTokenInfo createValidTokenInfo() {
        return JwtTokenService.JwtTokenInfo.builder()
                .hasToken(true)
                .tokenExpiry(Instant.now().plusSeconds(3600))
                .lastRefresh(Instant.now())
                .isValid(true)
                .refreshCount(1)
                .validationCount(10)
                .errorCount(0)
                .autoRefreshEnabled(true)
                .fileWatchingEnabled(false)
                .refreshBufferSeconds(300)
                .build();
    }

    private InMemoryConfigurationStore.CacheStatistics createCacheStatistics() {
        return InMemoryConfigurationStore.CacheStatistics.builder()
                .totalCacheSize(10)
                .consumerCacheSize(5)
                .producerCacheSize(5)
                .consumerCacheHits(100)
                .consumerCacheMisses(10)
                .producerCacheHits(150)
                .producerCacheMisses(15)
                .cacheEvictions(2)
                .defaultTtlMillis(7200000)
                .maxCacheSize(50000)
                .lastEvictionTime(Instant.now())
                .averageLoadTime(100)
                .build();
    }

    private ManagementNodeDataHandler.ManagementNodeStatistics createManagementNodeStatistics() {
        return ManagementNodeDataHandler.ManagementNodeStatistics.builder()
                .totalRequests(100)
                .successfulRequests(95)
                .failedRequests(5)
                .lastRequestTime(Instant.now())
                .isHealthy(true)
                .build();
    }
}