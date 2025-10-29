package uk.gov.dbt.ndtp.federator.common.service.stream;

import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.utils.ProducerConsumerConfigServiceFactory;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;

public interface FederatorStreamService<R, T> {
    void streamToClient(R request, StreamObservable<T> streamObservable);

    default ProducerConfigDTO getProducerConfiguration() {
        return ProducerConsumerConfigServiceFactory.getProducerConfigService().getProducerConfiguration();
    }
}
