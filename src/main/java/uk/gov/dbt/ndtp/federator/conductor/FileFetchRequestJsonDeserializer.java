package uk.gov.dbt.ndtp.federator.conductor;

import uk.gov.dbt.ndtp.federator.model.FileFetchRequest;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.serializers.AbstractJacksonDeserializer;

public class FileFetchRequestJsonDeserializer extends AbstractJacksonDeserializer<FileFetchRequest> {

    public FileFetchRequestJsonDeserializer() {
        super(FileFetchRequest.class);
    }
}
