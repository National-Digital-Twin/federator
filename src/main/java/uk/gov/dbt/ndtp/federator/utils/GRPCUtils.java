// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenServiceClientSecretImpl;
import uk.gov.dbt.ndtp.federator.service.IdpTokenServiceMtlsImpl;

public class GRPCUtils {
    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private static final String IDP_MTLS_ENABLED_PROPERTY = "idp.mtls.enabled";

    private static final Logger LOGGER = LoggerFactory.getLogger("GRPCUtils");

    private GRPCUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static IdpTokenService createIdpTokenService() {

        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        boolean isMtlsEnabled = Boolean.parseBoolean(properties.getProperty(IDP_MTLS_ENABLED_PROPERTY, "false"));
        LOGGER.warn("===========Idp mTLS enabled: {}============", isMtlsEnabled);

        HttpClient client = isMtlsEnabled
                ? HttpClientFactoryUtils.createHttpClientWithMtls(properties)
                : HttpClientFactoryUtils.createHttpClient(properties);

        ObjectMapper mapper = new ObjectMapper();
        return isMtlsEnabled
                ? new IdpTokenServiceMtlsImpl(client, mapper)
                : new IdpTokenServiceClientSecretImpl(client, mapper);
    }
}
