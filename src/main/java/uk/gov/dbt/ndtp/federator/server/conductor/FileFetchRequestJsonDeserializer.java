package uk.gov.dbt.ndtp.federator.server.conductor;

import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.serializers.AbstractJacksonDeserializer;

public class FileFetchRequestJsonDeserializer extends AbstractJacksonDeserializer<FileTransferRequest> {

    public FileFetchRequestJsonDeserializer() {
        super(FileTransferRequest.class);
    }
}
