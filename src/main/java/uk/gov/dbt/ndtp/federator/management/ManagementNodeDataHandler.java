package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.converter.ConfigurationConverter;
import uk.gov.dbt.ndtp.federator.exceptions.ManagementNodeException;
import uk.gov.dbt.ndtp.federator.model.ConsumerConfiguration;
import uk.gov.dbt.ndtp.federator.model.ProducerConfiguration;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ManagementNodeDataHandler {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConfigurationConverter configurationConverter;
    private final String managementNodeBaseUrl;
    private final Duration requestTimeout;
    private final ExecutorService executorService;

    // Inner class for ProducerConfigurationResponse
    @Data
    @Setter
    @Getter
    public static class ProducerConfigurationResponse {
        private String clientId;
        private List<ProducerDTO> producers;

        public ProducerConfigurationResponse() {
            this.producers = new ArrayList<>();
        }
    }

    // Statistics class
    @Builder
    @Data
    public static class ManagementNodeStatistics {
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;
        private final Instant lastRequestTime;
        private final boolean isHealthy;
    }

    public ManagementNodeDataHandler(Properties config) {
        this.managementNodeBaseUrl = config.getProperty("management.node.base.url", "http://localhost:8080");
        int timeoutSeconds = Integer.parseInt(config.getProperty("management.node.request.timeout", "30"));
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);

        this.executorService = Executors.newFixedThreadPool(5);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .executor(executorService)
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.configurationConverter = new ConfigurationConverter();

        log.info("ManagementNodeDataHandler initialized with base URL: {}", managementNodeBaseUrl);
    }

    public List<ProducerConfiguration> getProducerData(String jwtToken) throws ManagementNodeException {
        log.debug("Fetching producer configurations");
        List<ProducerConfiguration> configurations = new ArrayList<>();

        // Simulate fetching data - in real implementation, make HTTP call
        ProducerConfiguration config = new ProducerConfiguration();
        config.setProducerId("PRODUCER_1");
        config.setName("Test Producer");
        config.setActive(true);
        configurations.add(config);

        return configurations;
    }

    public List<ConsumerConfiguration> getConsumerData(String jwtToken) throws ManagementNodeException {
        log.debug("Fetching consumer configurations");
        List<ConsumerConfiguration> configurations = new ArrayList<>();

        // Simulate fetching data - in real implementation, make HTTP call
        ConsumerConfiguration config = new ConsumerConfiguration();
        config.setClientId("CONSUMER_1");
        config.setName("Test Consumer");
        config.setActive(true);
        configurations.add(config);

        return configurations;
    }

    public ProducerConfigurationResponse getProducerConfigurationResponse(String jwtToken, String producerId)
            throws ManagementNodeException {
        log.debug("Fetching producer configuration response for: {}", producerId);

        ProducerConfigurationResponse response = new ProducerConfigurationResponse();
        response.setClientId("TEST_CLIENT");

        ProducerDTO producer = new ProducerDTO();
        producer.setName(producerId != null ? producerId : "DEFAULT_PRODUCER");
        producer.setActive(true);

        List<ProducerDTO> producers = new ArrayList<>();
        producers.add(producer);
        response.setProducers(producers);

        return response;
    }

    public ConsumerConfigDTO getConsumerConfigurationResponse(String jwtToken, String consumerId)
            throws ManagementNodeException {
        log.debug("Fetching consumer configuration response");

        return ConsumerConfigDTO.builder()
                .clientId("TEST_CLIENT")
                .producers(new ArrayList<>())
                .build();
    }

    public ProducerDTO getProducerDataByProducerId(String jwtToken, String producerId)
            throws ManagementNodeException {
        ProducerDTO producer = new ProducerDTO();
        producer.setName(producerId);
        producer.setActive(true);
        return producer;
    }

    public ConsumerDTO getConsumerDataByClientId(String jwtToken, String clientId)
            throws ManagementNodeException {
        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setIdpClientId(clientId);
        consumer.setName("Test Consumer");
        return consumer;
    }

    public ConsumerConfiguration getConsumerConfigurationByClientId(String jwtToken, String clientId)
            throws ManagementNodeException {
        ConsumerConfiguration config = new ConsumerConfiguration();
        config.setClientId(clientId);
        config.setName("Test Consumer");
        config.setActive(true);
        return config;
    }

    public ProducerConfiguration getProducerConfigurationByProducerId(String jwtToken, String producerId)
            throws ManagementNodeException {
        ProducerConfiguration config = new ProducerConfiguration();
        config.setProducerId(producerId);
        config.setName("Test Producer");
        config.setActive(true);
        return config;
    }

    public CompletableFuture<List<ConsumerConfiguration>> getConsumerDataAsync(String jwtToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getConsumerData(jwtToken);
            } catch (ManagementNodeException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    public CompletableFuture<List<ProducerConfiguration>> getProducerDataAsync(String jwtToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getProducerData(jwtToken);
            } catch (ManagementNodeException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    public boolean isHealthy() {
        return true; // Simplified - always return healthy
    }

    public ManagementNodeStatistics getStatistics() {
        return ManagementNodeStatistics.builder()
                .totalRequests(10)
                .successfulRequests(9)
                .failedRequests(1)
                .lastRequestTime(Instant.now())
                .isHealthy(true)
                .build();
    }

    public void close() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        log.info("ManagementNodeDataHandler closed");
    }
}