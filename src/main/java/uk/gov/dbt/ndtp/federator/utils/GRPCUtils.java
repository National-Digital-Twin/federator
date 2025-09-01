package uk.gov.dbt.ndtp.federator.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.Properties;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenServiceImpl;

public class GRPCUtils {
    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";

    private GRPCUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static IdpTokenService createIdpTokenServiceWithSsl(boolean useSsl) {

        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        HttpClient client = useSsl
                ? HttpClientFactoryUtils.createHttpClientWithSsl(properties)
                : HttpClientFactoryUtils.createHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        return new IdpTokenServiceImpl(client, mapper);
    }
}
