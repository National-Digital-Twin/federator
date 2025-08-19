package uk.gov.dbt.ndtp.federator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import uk.gov.dbt.ndtp.federator.converter.ConfigurationConverter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consumer Configuration model for internal use
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerConfiguration {
    private String clientId;
    private String name;
    private String description;
    private boolean active;
    private String filterClassname;
    private List<Topic> topics;  // Changed from TopicConfiguration to Topic
    private ApiConfiguration api;
    private KafkaConfiguration kafka;
    private SecurityConfiguration security;
    private Map<String, String> metadata;
    private Instant createdAt;
    private Instant updatedAt;
}