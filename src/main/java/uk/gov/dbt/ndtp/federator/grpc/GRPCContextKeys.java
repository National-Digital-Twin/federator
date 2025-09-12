// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.grpc;

import io.grpc.Context;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;

public class GRPCContextKeys {
    public static final Context.Key<String> CLIENT_ID = Context.key(IdpTokenService.CLIENT_ID);

    // Add Private constructor to prevent instantiation
    private GRPCContextKeys() {}
}
