package uk.gov.dbt.ndtp.federator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KafkaConfiguration model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KafkaConfiguration {
    private String bootstrapServers;
    private String groupId;
    private String autoOffsetReset;
    private int maxPollRecords;
    private int sessionTimeoutMs;
    private boolean enableAutoCommit;
}