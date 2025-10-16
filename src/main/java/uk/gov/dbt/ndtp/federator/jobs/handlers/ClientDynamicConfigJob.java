// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.jobs.handlers;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.jobs.Job;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.JobsConstants;
import uk.gov.dbt.ndtp.federator.jobs.params.*;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProductConsumerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.service.ConsumerConfigService;

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
    private static final String LOG_JOB = "Job created [id={}, type={}, topic={}, host={}, port={}]";
    private static final String LOG_NULL = "Null producer skipped";
    private static final String LOG_CONNECTION = "Connection [server={}, host={}, port={}, tls={}]";
    private static final String LOG_ERROR_UNEXP = "Unexpected error [nodeId={}, error={}]";
    public static final String PRODUCT_TYPE_TOPIC = "topic";
    public static final String PRODUCT_TYPE_FILE = "file";


    private static ConsumerConfigService staticService;
    private static JobSchedulerProvider staticScheduler;
    private final ConsumerConfigService configService;
    private final JobSchedulerProvider scheduler;

    /** Creates a new job using static service reference. Used by JobRunr when deserializing jobs. */
    public ClientDynamicConfigJob() {
        this.configService = staticService;
        this.scheduler = staticScheduler;
        if (this.scheduler == null) {
            throw new IllegalStateException("Scheduler provider not initialized for ClientDynamicConfigJob");
        }
    }

    /**
     * Creates a new job with specified dependencies.
     *
     * @param configService the configuration service
     * @param scheduler the job scheduler provider
     */
    public ClientDynamicConfigJob(final ConsumerConfigService configService, final JobSchedulerProvider scheduler) {
        this.configService = configService;
        this.scheduler = scheduler;
    }

    /**
     * Initializes the static service reference. Must be called before JobRunr executes jobs.
     *
     * @param service the configuration service
     */
    public static void initialize(final ConsumerConfigService service) {
        staticService = service;
        log.debug("ClientDynamicConfigJob initialized");
    }

    /**
     * Sets the static scheduler provider used by default-constructed jobs (JobRunr path).
     */
    public static void setScheduler(final JobSchedulerProvider scheduler) {
        staticScheduler = scheduler;
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
        log.debug(LOG_JOB, request.getJobParams().getJobId(),product.getType(), product.getTopic(), conn.serverHost(), conn.serverPort());
    }

    private RecurrentJobRequest createJobRequest(
            final ProductDTO product, final ConnectionProperties conn, final String nodeId) {
        final String type = product.getType();
        if (type == null) {
            throw new IllegalArgumentException("Product type cannot be null for product: " + product.getName());
        }

        switch (type.toLowerCase()) {
            case PRODUCT_TYPE_TOPIC: {
                final ClientGRPCJobParams params = buildJobParams(product, conn, nodeId);
                final Job jobInstance = new ClientGRPCJob(params);
                return buildRecurrentJobRequest(jobInstance, params);
            }
            case PRODUCT_TYPE_FILE: {
                final ClientFileExchangeGRPCJobParams params = buildFileExchangeJobParams(product, conn, nodeId);
                final Job jobInstance = new ClientGRPCFileExchangeJob(params);
                return buildRecurrentJobRequest(jobInstance, params);
            }
            default:
                throw new IllegalArgumentException("Invalid product type:" + product.getType());
        }
    }

    private RecurrentJobRequest buildRecurrentJobRequest(final Job jobInstance, final JobParams params) {
        return RecurrentJobRequest.builder()
                .jobParams(params)
                .job(jobInstance)
                .build();
    }

    private <T extends JobParams> T applyCommonParams(final T params, final ProductDTO product, final ConnectionProperties conn) {
        params.setJobName(conn.serverName());
        params.setJobId(conn.serverName() + SEPARATOR + product.getTopic());
        final ProductConsumerDTO productConsumer = getProductConsumer(product);
        final String scheduleExpr = productConsumer.getScheduleExpression() != null
                ? productConsumer.getScheduleExpression()
                : JobsConstants.DEFAULT_DURATION_EVERY_HOUR;
        params.setScheduleExpression(scheduleExpr);
        params.setJobScheduleType(productConsumer.getScheduleType());
        return params;
    }

    private ClientGRPCJobParams buildJobParams(
            final ProductDTO product, final ConnectionProperties conn, final String nodeId) {
        final ClientGRPCJobParams params = new ClientGRPCJobParams(product.getTopic(), conn, nodeId);
        return applyCommonParams(params, product, conn);
    }


    private ClientFileExchangeGRPCJobParams buildFileExchangeJobParams(
            final ProductDTO product, final ConnectionProperties conn, final String nodeId) {

        ProductConsumerDTO productConsumer = getProductConsumer(product);
        final FileExchangeProperties fileExchangeProperties = FileExchangeProperties.builder()
                .sourcePath(product.getSource())
                .destinationPath(productConsumer.getDestination())
                .build();
        final ClientFileExchangeGRPCJobParams params = new ClientFileExchangeGRPCJobParams(fileExchangeProperties, product.getTopic(), conn, nodeId);
        return applyCommonParams(params, product, conn);
    }

    private  ProductConsumerDTO getProductConsumer(ProductDTO product) {
        if(product.getConfigurations().isEmpty()) {
            throw new IllegalArgumentException("Product configurations cannot be empty for product:" + product.getName());
        }
        if(product.getConfigurations().size() > 1) {
            log.warn("More than one configuration found for product:{}", product.getName());
            log.warn("1st option is picked up. this can result in unexpected behavior for product:{}", product.getName());

        }
        return product.getConfigurations().getFirst();
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
