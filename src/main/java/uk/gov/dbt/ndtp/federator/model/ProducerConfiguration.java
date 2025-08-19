package uk.gov.dbt.ndtp.federator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * ProducerConfiguration model for internal use
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProducerConfiguration {
    private String producerId;
    private String name;
    private String description;
    private String idpClientId;
    private boolean active;
    private String host;
    private int port;
    private boolean tls;
    private List<DataProvider> dataProviders;

    /**
     * Inner class for DataProvider
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DataProvider {
        private String name;
        private String topic;
        private String description;
        private boolean active;
        private List<ConsumerInfo> consumers;
    }

    /**
     * Inner class for ConsumerInfo
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConsumerInfo {
        private String name;
        private String idpClientId;
    }
}