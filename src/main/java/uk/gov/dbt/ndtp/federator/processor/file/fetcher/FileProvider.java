package uk.gov.dbt.ndtp.federator.processor.file.fetcher;

import uk.gov.dbt.ndtp.federator.model.FileTransferRequest;

public interface FileProvider {
    FileTransferResult get(FileTransferRequest request);
}
