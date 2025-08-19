package uk.gov.dbt.ndtp.federator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
        * Attribute Configuration model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeConfiguration {
    private String name;
    private String value;
    private String type;
    private String description;
    private boolean required;
    private boolean encrypted;
    private String validationPattern;
    private List<String> allowedValues;
    private Instant createdAt;
    private Instant updatedAt;
}