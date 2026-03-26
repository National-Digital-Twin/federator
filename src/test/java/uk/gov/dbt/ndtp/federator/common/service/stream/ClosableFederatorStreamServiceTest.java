package uk.gov.dbt.ndtp.federator.common.service.stream;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.service.file.FileStreamService;
import uk.gov.dbt.ndtp.federator.server.conductor.MessageConductor;

/**
 * Tests for {@link CloseableFederatorStreamService}
 */
class ClosableFederatorStreamServiceTest {

    @Test
    void shouldCloseAllMessageConductorsAndClearTheList_whenClosed() {
        CloseableFederatorStreamService service = new FileStreamService();

        MessageConductor messageConductor1 = mock(MessageConductor.class);
        service.messageConductors.add(messageConductor1);

        service.close();

        verify(messageConductor1, times(1)).close();
        assertTrue(service.messageConductors.isEmpty());
    }
}
