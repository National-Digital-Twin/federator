// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.jobs.handlers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.jobs.DefaultJobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.Job;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.params.ClientGRPCJobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.RecurrentJobRequest;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.service.ProducerConsumerConfigService;

/**
 * Job handler for dynamic configuration updates from Management Node. Fetches producer
 * configurations from the Management Node and creates GRPC jobs for each producer/product
 * combination.
 */
@Slf4j
public class ClientDynamicConfigJob implements Job {

    private static final String DEFAULT_NODE = "default";
    private static final String SEPARATOR = "-";
    private static final String NA_VALUE = "NA";
    private static final int TIMEOUT_SECONDS = 30;
    private static final String LOG_START = "Fetching configs from Management Node [nodeId={}]";
    private static final String LOG_ERROR = "Config fetch failed [nodeId={}, error={}]";
    private static final String LOG_RELOAD = "Reloading jobs [count={}, nodeId={}]";
    private static final String LOG_INVALID = "Invalid producer [name='{}', host='{}', port={}]";
    private static final String DEFAULT_PRODUCER = "Producer";
    private static final String HTTPS_PREFIX = "https://";
    private static final String HTTP_PREFIX = "http://";
    private static final String NON_ALPHANUMERIC = "[^a-zA-Z0-9]";
    private static final String EMPTY = "";
    private static final String LOG_SUCCESS = "Config fetched [producers={}, nodeId={}]";
    private static final String LOG_NO_CONFIG = "No config data received [nodeId={}]";
    private static final String LOG_JOB = "Job created [id={}, topic={}, host={}, port={}]";
    private static final String LOG_NULL = "Null producer skipped";
    private static final String LOG_CONNECTION = "Connection [server={}, host={}, port={}, tls={}]";
    private static final String LOG_ERROR_UNEXP = "Unexpected error [nodeId={}, error={}]";

    private static ProducerConsumerConfigService staticService;
    private final ProducerConsumerConfigService configService;
    private final JobSchedulerProvider scheduler;

    /** Creates a new job using static service reference. Used by JobRunr when deserializing jobs. */
    public ClientDynamicConfigJob() {
        this.configService = staticService;
        this.scheduler = DefaultJobSchedulerProvider.getInstance();
    }

    /**
     * Creates a new job with specified dependencies.
     *
     * @param configService the configuration service
     * @param scheduler the job scheduler provider
     */
    public ClientDynamicConfigJob(
            final ProducerConsumerConfigService configService, final JobSchedulerProvider scheduler) {
        this.configService = configService;
        this.scheduler = scheduler;
    }

    /**
     * Initializes the static service reference. Must be called before JobRunr executes jobs.
     *
     * @param service the configuration service
     */
    public static void initialize(final ProducerConsumerConfigService service) {
        staticService = service;
        log.debug("ClientDynamicConfigJob initialized");
    }

    /**
     * Executes the configuration update job.
     *
     * @param value the job parameters containing node ID
     */
    @Override
    public void run(final JobParams value) {
        final String nodeId = resolveNodeId(value);
        log.info(LOG_START, nodeId);

        try {
            final ConsumerConfigDTO config = configService.getConsumerConfiguration();
            if (config == null) {
                log.warn(LOG_NO_CONFIG, nodeId);
                return;
            }
            reloadJobs(config, nodeId);
        } catch (ManagementNodeDataException e) {
            log.error(LOG_ERROR, nodeId, e.getMessage(), e);
        } catch (Exception e) {
            log.error(LOG_ERROR_UNEXP, nodeId, e.getMessage(), e);
        }
    }

    private void reloadJobs(final ConsumerConfigDTO config, final String nodeId) {
        final List<RecurrentJobRequest> requests = buildJobRequests(config, nodeId);
        log.info(LOG_RELOAD, requests.size(), nodeId);
        scheduler.reloadRecurrentJobs(nodeId, requests);
    }

    private String resolveNodeId(final JobParams params) {
        if (params == null
                || params.getManagementNodeId() == null
                || params.getManagementNodeId().isBlank()) {
            return DEFAULT_NODE;
        }
        return params.getManagementNodeId();
    }

    private List<RecurrentJobRequest> buildJobRequests(final ConsumerConfigDTO config, final String nodeId) {
        final List<RecurrentJobRequest> requests = new ArrayList<>();

        if (config.getProducers() != null) {
            log.info(LOG_SUCCESS, config.getProducers().size(), nodeId);
            processProducers(config, nodeId, requests);
        }
        return requests;
    }

    private void processProducers(
            final ConsumerConfigDTO config, final String nodeId, final List<RecurrentJobRequest> requests) {
        for (ProducerDTO producer : config.getProducers()) {
            if (!isValidProducer(producer)) {
                logInvalidProducer(producer);
                continue;
            }
            processValidProducer(producer, nodeId, requests);
        }
    }

    private void processValidProducer(
            final ProducerDTO producer, final String nodeId, final List<RecurrentJobRequest> requests) {
        final ConnectionProperties conn = buildConnection(producer);
        processProducts(producer, conn, nodeId, requests);
    }

    private boolean isValidProducer(final ProducerDTO producer) {
        return producer != null
                && producer.getProducts() != null
                && producer.getName() != null
                && !producer.getName().trim().isEmpty()
                && producer.getHost() != null;
    }

    private void logInvalidProducer(final ProducerDTO producer) {
        if (producer == null) {
            log.warn(LOG_NULL);
            return;
        }
        log.warn(LOG_INVALID, producer.getName(), producer.getHost(), producer.getPort());
    }

    private ConnectionProperties buildConnection(final ProducerDTO producer) {
        final String serverName = cleanServerName(producer.getName());
        final String host = cleanHost(producer.getHost());
        final int port = getPort(producer);
        final boolean tls = getTls(producer);

        log.debug(LOG_CONNECTION, serverName, host, port, tls);

        return new ConnectionProperties(producer.getIdpClientId(), NA_VALUE, serverName, host, port, tls);
    }

    private int getPort(final ProducerDTO producer) {
        return producer.getPort() != null ? producer.getPort().intValue() : ConnectionProperties.DEFAULT_PORT;
    }

    private boolean getTls(final ProducerDTO producer) {
        return producer.getTls() != null ? producer.getTls() : ConnectionProperties.DEFAULT_TLS;
    }

    private String cleanServerName(final String name) {
        if (name == null || name.trim().isEmpty()) {
            return DEFAULT_PRODUCER;
        }
        final String cleaned = name.replaceAll(NON_ALPHANUMERIC, EMPTY);
        return cleaned.isEmpty() ? DEFAULT_PRODUCER : cleaned;
    }

    private String cleanHost(final String host) {
        if (host == null) {
            return host;
        }
        if (host.startsWith(HTTPS_PREFIX)) {
            return host.substring(HTTPS_PREFIX.length());
        }
        if (host.startsWith(HTTP_PREFIX)) {
            return host.substring(HTTP_PREFIX.length());
        }
        return host;
    }

    private void processProducts(
            final ProducerDTO producer,
            final ConnectionProperties conn,
            final String nodeId,
            final List<RecurrentJobRequest> requests) {
        for (ProductDTO product : producer.getProducts()) {
            if (isValidProduct(product)) {
                addJobRequest(product, conn, nodeId, requests);
            }
        }
    }

    private boolean isValidProduct(final ProductDTO product) {
        return product != null && product.getTopic() != null;
    }

    private void addJobRequest(
            final ProductDTO product,
            final ConnectionProperties conn,
            final String nodeId,
            final List<RecurrentJobRequest> requests) {
        final RecurrentJobRequest request = createJobRequest(product, conn, nodeId);
        requests.add(request);
        log.debug(LOG_JOB, request.getJobParams().getJobId(), product.getTopic(), conn.serverHost(), conn.serverPort());
    }

    private RecurrentJobRequest createJobRequest(
            final ProductDTO product, final ConnectionProperties conn, final String nodeId) {
        final ClientGRPCJobParams params = buildJobParams(product, conn, nodeId);
        return RecurrentJobRequest.builder()
                .jobParams(params)
                .job(new ClientGRPCJob(params))
                .build();
    }

    private ClientGRPCJobParams buildJobParams(
            final ProductDTO product, final ConnectionProperties conn, final String nodeId) {
        final ClientGRPCJobParams params = new ClientGRPCJobParams(product.getTopic(), conn, nodeId);
        params.setJobName(conn.serverName());
        params.setJobId(conn.serverName() + SEPARATOR + product.getTopic()); // FIX: Was duplicating serverName
        params.setDuration(Duration.ofSeconds(TIMEOUT_SECONDS));
        return params;
    }

    /**
     * Returns string representation of this job.
     *
     * @return job description
     */
    @Override
    public String toString() {
        return "Client Dynamic Config Job";
    }
}
