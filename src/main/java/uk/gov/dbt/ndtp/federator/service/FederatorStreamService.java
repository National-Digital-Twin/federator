package uk.gov.dbt.ndtp.federator.service;

import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.utils.ProducerConsumerConfigServiceFactory;

public interface FederatorStreamService<R, T> {
    void streamToClient(R request, StreamObservable<T> streamObservable);

    default ProducerConfigDTO getProducerConfiguration() {
        return ProducerConsumerConfigServiceFactory.getProducerConfigService().getProducerConfiguration();
    }
}
