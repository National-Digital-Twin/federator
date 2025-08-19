package uk.gov.dbt.ndtp.federator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple Topic model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic {
    private String name;
    private String description;
    private boolean active;
}