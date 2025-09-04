package uk.gov.dbt.ndtp.federator.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenServiceClientSecretImpl;
import uk.gov.dbt.ndtp.federator.service.IdpTokenServiceMtlsImpl;

@Slf4j
public class GRPCUtils {
    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private static final String IDP_MTLS_ENABLED_PROPERTY ="idp.mtls.enabled";

    private GRPCUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static IdpTokenService createIdpTokenService() {

        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        boolean isMtlsEnabled = Boolean.parseBoolean(properties.getProperty(IDP_MTLS_ENABLED_PROPERTY, "false"));
        log.warn("===========Idp mTLS enabled: {}============", isMtlsEnabled);

        HttpClient client = isMtlsEnabled
                ? HttpClientFactoryUtils.createHttpClientWithMtls(properties)
                : HttpClientFactoryUtils.createHttpClient(properties);

        ObjectMapper mapper = new ObjectMapper();
        IdpTokenService tokenService = isMtlsEnabled ? new IdpTokenServiceMtlsImpl(client, mapper)
                : new IdpTokenServiceClientSecretImpl(client, mapper);
        return tokenService;
    }
}
