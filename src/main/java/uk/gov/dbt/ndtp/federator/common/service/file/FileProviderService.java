package uk.gov.dbt.ndtp.federator.common.service.file;

import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.FileProvider;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.FileProviderFactory;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.FileTransferResult;

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
