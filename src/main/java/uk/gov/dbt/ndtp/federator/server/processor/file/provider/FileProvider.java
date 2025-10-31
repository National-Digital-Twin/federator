package uk.gov.dbt.ndtp.federator.server.processor.file.provider;

import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;

public interface FileProvider {
    FileTransferResult get(FileTransferRequest request);
}
