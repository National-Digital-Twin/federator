package uk.gov.dbt.ndtp.federator.grpc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.utils.ClientFilter;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.SSLUtils;

public class GRPCServerTest {

    @Test
    void testGRPCServerWithInsecureServer() {
        try (MockedStatic<PropertyUtil> propertyUtilMockedStatic = mockStatic(PropertyUtil.class)) {
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyBooleanValue(GRPCServer.SERVER_MTLS_ENABLED, GRPCServer.FALSE))
                    .thenReturn(false);
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyIntValue(GRPCServer.SERVER_PORT, GRPCServer.DEFAULT_PORT))
                    .thenReturn(8080);
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyIntValue(GRPCServer.SERVER_KEEP_ALIVE_TIME, GRPCServer.FIVE))
                    .thenReturn(5);
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyIntValue(GRPCServer.SERVER_KEEP_ALIVE_TIMEOUT, GRPCServer.ONE))
                    .thenReturn(1);

            List<ClientFilter> filters = Collections.emptyList();
            Set<String> sharedHeaders = new HashSet<>();

            GRPCServer server = new GRPCServer(filters, sharedHeaders);
            assertNotNull(server);
        }
    }

    @Test
    void testGRPCServerWithSecureServer() {
        try (MockedStatic<PropertyUtil> propertyUtilMockedStatic = mockStatic(PropertyUtil.class)) {
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyBooleanValue(GRPCServer.SERVER_MTLS_ENABLED, GRPCServer.FALSE))
                    .thenReturn(true);
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyIntValue(GRPCServer.SERVER_PORT, GRPCServer.DEFAULT_PORT))
                    .thenReturn(8080);
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyIntValue(GRPCServer.SERVER_KEEP_ALIVE_TIME, GRPCServer.FIVE))
                    .thenReturn(5);
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyIntValue(GRPCServer.SERVER_KEEP_ALIVE_TIMEOUT, GRPCServer.ONE))
                    .thenReturn(1);
            propertyUtilMockedStatic
                    .when(() -> PropertyUtil.getPropertyValue(any()))
                    .thenReturn("dummy");

            try (MockedStatic<SSLUtils> sslUtilsMockedStatic = mockStatic(SSLUtils.class)) {
                X509KeyManager mockKeyManager = mock(X509KeyManager.class);
                X509TrustManager mockTrustManager = mock(X509TrustManager.class);

                sslUtilsMockedStatic
                        .when(() -> SSLUtils.createKeyManagerFromP12(anyString(), anyString()))
                        .thenReturn(new KeyManager[] {mockKeyManager});
                sslUtilsMockedStatic
                        .when(() -> SSLUtils.createTrustManager(anyString(), anyString()))
                        .thenReturn(new TrustManager[] {mockTrustManager});

                List<ClientFilter> filters = Collections.emptyList();
                Set<String> sharedHeaders = new HashSet<>();

                try (MockedStatic<ServerBuilder> serverBuilderMockedStatic = mockStatic(ServerBuilder.class)) {
                    ServerBuilder<?> serverBuilder = mock(ServerBuilder.class, Mockito.RETURNS_SELF);
                    Mockito.when(serverBuilder.build()).thenReturn(mock(Server.class));
                    serverBuilderMockedStatic
                            .when(() -> ServerBuilder.forPort(any(Integer.class)))
                            .thenReturn(serverBuilder);

                    GRPCServer server = new GRPCServer(filters, sharedHeaders);
                    assertNotNull(server);
                }
            }
        }
    }
}
