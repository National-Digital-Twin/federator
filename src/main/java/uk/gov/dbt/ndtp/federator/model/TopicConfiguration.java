package uk.gov.dbt.ndtp.federator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Topic model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicConfiguration {
    private String name;
    private String description;
    private boolean active;
    private List<String> allowedOperations;
}