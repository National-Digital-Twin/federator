package uk.gov.dbt.ndtp.federator.integration.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.integration.model.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.integration.model.ProducerDTO;
import uk.gov.dbt.ndtp.federator.integration.model.ProductDTO;

@Slf4j
public class ManagementNodeService {

    private final Object lock = new Object();
    private volatile ProducerConfigDTO producerConfig;

    private static List<ConnectionProperties> buildConnectionProperties(ProducerConfigDTO config) {
        List<ProducerDTO> producers = config.getProducers();
        if (producers == null || producers.isEmpty()) {
            return Collections.emptyList();
        }

        final int defaultPort = ConnectionProperties.DEFAULT_PORT;
        final boolean defaultTls = ConnectionProperties.DEFAULT_TLS;

        List<ConnectionProperties> connections = new ArrayList<>(producers.size());
        for (ProducerDTO p : producers) {
            if (p == null) {
                continue;
            }

            String host = p.getHost();
            BigDecimal portBD = p.getPort();
            int port = portBD == null ? defaultPort : portBD.intValue();
            boolean tls = Objects.requireNonNullElse(p.getTls(), defaultTls);

            connections.add(new ConnectionProperties(p.getIdpClientId(),"NA", p.getName(), host, port, tls));
        }
        return connections;
    }

    public ProducerConfigDTO getProducerConfig() {
    synchronized (lock) {
      // Return cached if already loaded
      if (producerConfig != null) {
        return producerConfig;
      }

      // 1) Try load from classpath JSON resource
      try (InputStream is =
          ManagementNodeService.class.getResourceAsStream("/producer-config.json")) {
        if (is != null) {
          ObjectMapper mapper =
              new ObjectMapper()
                  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
          producerConfig = mapper.readValue(is, ProducerConfigDTO.class);
        }
      } catch (Exception e) {
        // Fall through to sample generation if parsing fails
      }
        }
        return producerConfig;

    }

    /**
     * Returns the list of products (dataProviders) for a given producer name from the loaded
     * producer configuration. If the producer cannot be found or has no products, an empty list
     * is returned. The returned list is unmodifiable.
     *
     * @param producerName the name of the producer to search for (case-insensitive)
     * @return unmodifiable list of ProductDTOs or an empty list if not found
     */
    public List<ProductDTO> getProductsByProducerName(String producerName) {
        if (producerName == null || producerName.isBlank()) {
            return Collections.emptyList();
        }
        ProducerConfigDTO cfg = getProducerConfig();
        if (cfg == null || cfg.getProducers() == null || cfg.getProducers().isEmpty()) {
            return Collections.emptyList();
        }
        for (ProducerDTO p : cfg.getProducers()) {
            if (p != null && p.getName() != null && p.getName().equalsIgnoreCase(producerName)) {
                List<ProductDTO> providers = p.getDataProviders();
                if (providers == null || providers.isEmpty()) {
                    return Collections.emptyList();
                }
                return Collections.unmodifiableList(new ArrayList<>(providers));
            }
        }
        return Collections.emptyList();
    }

    /** Convenience overload that takes a ProducerConfigDTO. */
    public List<ConnectionProperties> getConnectionProperties(String managementNodeId) {
        log.info("Getting connection properties from management node {}", managementNodeId);
        synchronized (lock) {
            if (producerConfig == null)
                producerConfig= getProducerConfig();

            List<ConnectionProperties> result = buildConnectionProperties(producerConfig);
            return Collections.unmodifiableList(result);
        }
    }


}
