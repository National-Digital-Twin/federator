package uk.gov.dbt.ndtp.federator.service.file;

import uk.gov.dbt.ndtp.federator.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileProvider;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileProviderFactory;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileTransferResult;

public class FileProviderService {

    /**
     * Fetches the file specified in the FileTransferRequest using the appropriate FileProvider.
     * @param request
     * @return
     */
    public FileTransferResult getFile(FileTransferRequest request) {
        FileProvider fetcher = FileProviderFactory.getProvider(request.sourceType());
        return fetcher.get(request);
    }
}
