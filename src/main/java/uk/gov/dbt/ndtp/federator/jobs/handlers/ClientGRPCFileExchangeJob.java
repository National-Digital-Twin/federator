package uk.gov.dbt.ndtp.federator.jobs.handlers;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.jobs.Job;
import uk.gov.dbt.ndtp.federator.jobs.params.ClientFileExchangeGRPCJobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;

@Slf4j
public class ClientGRPCFileExchangeJob implements Job {




    @Setter
    private ClientFileExchangeGRPCJobParams request;

    /** Default constructor wires real implementations for backward compatibility. */
    public ClientGRPCFileExchangeJob() {

    }

    /** Convenience constructor to set initial request using default wiring. */
    public ClientGRPCFileExchangeJob(ClientFileExchangeGRPCJobParams request) {
        this();
        this.request = request;
    }

    @Override
    public void run(JobParams value) {
        if (request == null) {
            request = (ClientFileExchangeGRPCJobParams) value;
        }

        log.info("running File Exchange Job:");
        log.info("requesting topic:{}", request.getTopic());
        log.info("source:{}", request.getFileExchangeProperties().getSourcePath());
        log.info("destination:{}", request.getFileExchangeProperties().getDestinationPath());


    }

    @Override
    public String toString() {
        return "Client File Exchange Over GRPC Job";
    }
}
