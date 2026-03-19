package uk.gov.dbt.ndtp.federator.common.service.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uk.gov.dbt.ndtp.federator.server.conductor.MessageConductor;

/**
 * An abstract class that implements both the {@link FederatorStreamService<R, T>} and {@link AutoCloseable}
 */
public abstract class CloseableFederatorStreamService<R, T> implements FederatorStreamService<R, T>, AutoCloseable {
    protected final List<MessageConductor> messageConductors = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void close() {
        for (MessageConductor messageConductor : messageConductors) {
            messageConductor.close();
        }

        messageConductors.clear();
    }
}
