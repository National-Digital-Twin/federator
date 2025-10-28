package uk.gov.dbt.ndtp.federator.conductor;

import uk.gov.dbt.ndtp.federator.model.FileTransferRequest;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.serializers.AbstractJacksonDeserializer;

public class FileFetchRequestJsonDeserializer extends AbstractJacksonDeserializer<FileTransferRequest> {

    public FileFetchRequestJsonDeserializer() {
        super(FileTransferRequest.class);
    }
}
