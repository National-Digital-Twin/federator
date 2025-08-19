package uk.gov.dbt.ndtp.federator.converter;

import uk.gov.dbt.ndtp.federator.model.*;
import uk.gov.dbt.ndtp.federator.model.dto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converter class to transform DTOs to Configuration models.
 * Handles conversion between API DTOs and internal domain models.
 */
public class ConfigurationConverter {

    /**
     * Converts ProducerDTO to ProducerConfiguration
     */
    public ProducerConfiguration convertToProducerConfiguration(ProducerDTO dto) {
        if (dto == null) {
            return null;
        }

        ProducerConfiguration configuration = ProducerConfiguration.builder()
                .producerId(dto.getName()) // Using name as producer ID
                .name(dto.getName())
                .description(dto.getDescription())
                .idpClientId(dto.getIdpClientId())
                .active(dto.getActive() != null ? dto.getActive() : false)
                .host(dto.getHost())
                .port(dto.getPort() != null ? dto.getPort().intValue() : 443)
                .tls(dto.getTls() != null ? dto.getTls() : true)
                .build();

        // Convert DataProviders to internal structure
        if (dto.getDataProviders() != null) {
            List<ProducerConfiguration.DataProvider> dataProviders = dto.getDataProviders().stream()
                    .map(this::convertToDataProvider)
                    .toList();            configuration.setDataProviders(dataProviders);
        } else {
            configuration.setDataProviders(new ArrayList<>());
        }

        return configuration;
    }

    /**
     * Converts ConsumerDTO to ConsumerConfiguration
     */
    public ConsumerConfiguration convertToConsumerConfiguration(ConsumerDTO dto) {
        if (dto == null) {
            return null;
        }

        ConsumerConfiguration configuration = ConsumerConfiguration.builder()
                .clientId(dto.getIdpClientId())
                .name(dto.getName())
                .active(true)
                .filterClassname(null)
                .topics(new ArrayList<>())
                .build();

        // Create default API configuration
        ApiConfiguration api = ApiConfiguration.builder()
                .revoked(false)
                .endpoint("")
                .apiKey("")
                .rateLimit(1000)
                .build();
        configuration.setApi(api);

        // Create default Kafka configuration
        KafkaConfiguration kafka = KafkaConfiguration.builder()
                .bootstrapServers("localhost:9092")
                .groupId(dto.getIdpClientId() + "-group")
                .autoOffsetReset("earliest")
                .maxPollRecords(500)
                .sessionTimeoutMs(30000)
                .enableAutoCommit(false)
                .build();
        configuration.setKafka(kafka);

        // Create default Security configuration
        SecurityConfiguration security = SecurityConfiguration.builder()
                .protocol("SASL_SSL")
                .saslMechanism("PLAIN")
                .build();
        configuration.setSecurity(security);

        return configuration;
    }

    /**
     * Converts DataProviderDTO to ProducerConfiguration.DataProvider
     */
    public ProducerConfiguration.DataProvider convertToDataProvider(DataProviderDTO dto) {
        if (dto == null) {
            return null;
        }

        ProducerConfiguration.DataProvider dataProvider = ProducerConfiguration.DataProvider.builder()
                .name(dto.getName())
                .topic(dto.getTopic())
                .description(dto.getDescription())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        // Convert consumers
        if (dto.getConsumers() != null) {
            List<ProducerConfiguration.ConsumerInfo> consumerInfos = dto.getConsumers().stream()
                    .map(this::convertToConsumerInfo)
                    .toList();            dataProvider.setConsumers(consumerInfos);
        } else {
            dataProvider.setConsumers(new ArrayList<>());
        }

        return dataProvider;
    }

    /**
     * Converts ConsumerDTO to ProducerConfiguration.ConsumerInfo
     */
    public ProducerConfiguration.ConsumerInfo convertToConsumerInfo(ConsumerDTO dto) {
        if (dto == null) {
            return null;
        }

        return ProducerConfiguration.ConsumerInfo.builder()
                .name(dto.getName())
                .idpClientId(dto.getIdpClientId())
                .build();
    }

    /**
     * Converts ProducerConfigDTO to list of ProducerConfigurations
     */
    public List<ProducerConfiguration> convertProducerConfigResponse(ProducerConfigDTO configDTO) {
        if (configDTO == null || configDTO.getProducers() == null) {
            return new ArrayList<>();
        }

        return configDTO.getProducers().stream()
                .map(this::convertToProducerConfiguration)
                .toList();    }

    /**
     * Converts ConsumerConfigDTO to list of ConsumerConfigurations
     */
    public List<ConsumerConfiguration> convertConsumerConfigResponse(ConsumerConfigDTO configDTO) {
        if (configDTO == null || configDTO.getProducers() == null) {
            return new ArrayList<>();
        }

        List<ConsumerConfiguration> consumers = new ArrayList<>();

        // Extract consumers from the nested structure
        for (ProducerDTO producer : configDTO.getProducers()) {
            if (producer.getDataProviders() != null) {
                for (DataProviderDTO dataProvider : producer.getDataProviders()) {
                    if (dataProvider.getConsumers() != null) {
                        for (ConsumerDTO consumer : dataProvider.getConsumers()) {
                            ConsumerConfiguration config = convertToConsumerConfiguration(consumer);

                            // Add the topic this consumer has access to
                            Topic topic = Topic.builder()
                                    .name(dataProvider.getTopic())
                                    .description(dataProvider.getDescription())
                                    .active(dataProvider.getActive() != null ? dataProvider.getActive() : true)
                                    .build();

                            if (config.getTopics() == null) {
                                config.setTopics(new ArrayList<>());
                            }
                            config.getTopics().add(topic);

                            consumers.add(config);
                        }
                    }
                }
            }
        }

        return consumers;
    }

    /**
     * Converts a list of ProducerConfigurations to ProducerConfigDTO
     */
    public ProducerConfigDTO convertToProducerConfigDTO(String clientId, List<ProducerConfiguration> configurations) {
        if (configurations == null) {
            return ProducerConfigDTO.builder()
                    .clientId(clientId)
                    .producers(new ArrayList<>())
                    .build();
        }

        List<ProducerDTO> producerDTOs = configurations.stream()
                .map(this::convertToProducerDTO)
                .toList();
        return ProducerConfigDTO.builder()
                .clientId(clientId)
                .producers(producerDTOs)
                .build();
    }

    /**
     * Converts ProducerConfiguration to ProducerDTO
     */
    public ProducerDTO convertToProducerDTO(ProducerConfiguration configuration) {
        if (configuration == null) {
            return null;
        }

        ProducerDTO dto = ProducerDTO.builder()
                .name(configuration.getName())
                .description(configuration.getDescription())
                .idpClientId(configuration.getIdpClientId())
                .active(configuration.isActive())
                .host(configuration.getHost())
                .port(configuration.getPort() != 0 ?
                        java.math.BigDecimal.valueOf(configuration.getPort()) :
                        java.math.BigDecimal.valueOf(443))
                .tls(configuration.isTls())
                .build();

        // Convert DataProviders
        if (configuration.getDataProviders() != null) {
            List<DataProviderDTO> dataProviderDTOs = configuration.getDataProviders().stream()
                    .map(this::convertToDataProviderDTO)
                    .toList();            dto.setDataProviders(dataProviderDTOs);
        } else {
            dto.setDataProviders(new ArrayList<>());
        }

        return dto;
    }

    /**
     * Converts ProducerConfiguration.DataProvider to DataProviderDTO
     */
    public DataProviderDTO convertToDataProviderDTO(ProducerConfiguration.DataProvider dataProvider) {
        if (dataProvider == null) {
            return null;
        }

        DataProviderDTO dto = DataProviderDTO.builder()
                .name(dataProvider.getName())
                .topic(dataProvider.getTopic())
                .description(dataProvider.getDescription())
                .active(dataProvider.isActive())
                .build();

        // Convert consumers
        if (dataProvider.getConsumers() != null) {
            List<ConsumerDTO> consumerDTOs = dataProvider.getConsumers().stream()
                    .map(this::convertConsumerInfoToDTO)
                    .toList();            dto.setConsumers(consumerDTOs);
        } else {
            dto.setConsumers(new ArrayList<>());
        }

        return dto;
    }

    /**
     * Converts ProducerConfiguration.ConsumerInfo to ConsumerDTO
     */
    public ConsumerDTO convertConsumerInfoToDTO(ProducerConfiguration.ConsumerInfo consumerInfo) {
        if (consumerInfo == null) {
            return null;
        }

        return ConsumerDTO.builder()
                .name(consumerInfo.getName())
                .idpClientId(consumerInfo.getIdpClientId())
                .build();
    }

    /**
     * Converts ConsumerConfiguration to ConsumerDTO
     */
    public ConsumerDTO convertToConsumerDTO(ConsumerConfiguration configuration) {
        if (configuration == null) {
            return null;
        }

        return ConsumerDTO.builder()
                .name(configuration.getName())
                .idpClientId(configuration.getClientId())
                .build();
    }

    /**
     * Creates a Topic from name and description
     */
    public Topic createTopic(String name, String description) {
        return Topic.builder()
                .name(name)
                .description(description)
                .active(true)
                .build();
    }
}