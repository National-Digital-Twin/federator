/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.jobs.params;

import java.util.Objects;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileExchangeProperties {
    String sourcePath;

    String destinationPath;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FileExchangeProperties that = (FileExchangeProperties) o;
        return Objects.equals(getSourcePath(), that.getSourcePath())
                && Objects.equals(getDestinationPath(), that.getDestinationPath());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSourcePath(), getDestinationPath());
    }

    @Override
    public String toString() {
        return "FileExchangeProperties{" + "sourcePath='"
                + sourcePath + '\'' + ", destinationPath='"
                + destinationPath + '\'' + '}';
    }
}
