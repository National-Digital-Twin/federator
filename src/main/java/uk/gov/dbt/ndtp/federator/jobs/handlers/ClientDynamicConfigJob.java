package uk.gov.dbt.ndtp.federator.jobs.handlers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.integration.model.ProductDTO;
import uk.gov.dbt.ndtp.federator.integration.services.ManagementNodeService;
import uk.gov.dbt.ndtp.federator.jobs.DefaultJobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.Job;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.params.ClientGRPCJobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.RecurrentJobRequest;

@Slf4j
public class ClientDynamicConfigJob implements Job {

    private final ManagementNodeService managementNodeService;

    private final JobSchedulerProvider schedulerProvider;

    /**
     * Default constructor for backward compatibility: wires default dependencies.
     */
    public ClientDynamicConfigJob() {
        this(new ManagementNodeService(), DefaultJobSchedulerProvider.getInstance());
    }

    /**
     * Constructor with externally provided dependencies to facilitate testing and decouple from
     * concrete implementations.
     */
    public ClientDynamicConfigJob(ManagementNodeService managementNodeService, JobSchedulerProvider schedulerProvider) {
        this.managementNodeService = managementNodeService;
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(JobParams value) {
        log.info("Contacting Management Node and check for new client configurations");
        /*
         * This method will call Management Node and fetch the latest configuration for this client
         * if the Hash is different, then it loads the configurations again
         */

        // we can have multiple ManagementNodes. so what we retrieve depends on the list of
        // managementNodes in the configs
        String providedNodeId = value != null ? value.getManagementNodeId() : null;
        String managementNodeId = (providedNodeId == null || providedNodeId.isBlank()) ? "default" : providedNodeId;

        List<ConnectionProperties> sampleConfigurations =
                managementNodeService.getConnectionProperties(managementNodeId);
        List<RecurrentJobRequest> jobRequests = new ArrayList<>();

        // prepare jobs to be created
        sampleConfigurations.forEach(config -> {
            List<ProductDTO> productsByProducerName =
                    managementNodeService.getProductsByProducerName(config.serverName());
            productsByProducerName.forEach(topic -> {
                ClientGRPCJobParams clientGRPCJobParams = getClientGRPCJobParams(config, topic, managementNodeId);

                ClientGRPCJob job = new ClientGRPCJob(clientGRPCJobParams);
                RecurrentJobRequest request = RecurrentJobRequest.builder()
                        .jobParams(clientGRPCJobParams)
                        .job(job)
                        .build();
                jobRequests.add(request);
            });
        });

        schedulerProvider.reloadRecurrentJobs(managementNodeId, jobRequests);
    }

    private static ClientGRPCJobParams getClientGRPCJobParams(
            ConnectionProperties config, ProductDTO topic, String managementNodeId) {
        ClientGRPCJobParams clientGRPCJobParams = new ClientGRPCJobParams(topic.getTopic(), config, managementNodeId);
        clientGRPCJobParams.setJobName(config.serverName());
        clientGRPCJobParams.setJobId(config.serverName() + "-" + config.serverName());
        clientGRPCJobParams.setDuration(Duration.ofSeconds(30));
        clientGRPCJobParams.setManagementNodeId(managementNodeId);
        return clientGRPCJobParams;
    }

    @Override
    public String toString() {
        return "Client Dynamic Config Job";
    }
}
