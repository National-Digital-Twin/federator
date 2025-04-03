// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */
package uk.gov.dbt.ndtp.federator.access;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessDetails;

/**
 * Represents message sent by Access component.
 * <p>
 * Note: Only implemented 2 possible actions at present
 *  - create user
 *  - update user (topics)
 * <p>
 *  TODO - identify and implement other actions
 *  revoke?
 *  delete?
 */
@Getter
@Setter
@NoArgsConstructor
public class AccessMessage {

    /*
    // Create Message
    {
        "action": "create",
        "client": "ndtp.dbt.gov.uk",
        "body":
        {
            "registered_client": "ndtp.dbt.gov.uk",
            "topics": null,
            "api":
            {
                "hashed_key": "$2a$14$Ncmi.hgzOXc3cUgzvH24mOgEjRzAcx6/oSNYEbxapEFHbeaOrM6Kq",
                "issued": "2023-05-05T15:48:12.305746+01:00",
                "revoked": false
            },
            "attributes":
            {
                "nationality": "GBR",
                "clearance": "O",
                "organisation_type": "NON-GOV"
            }
        }
    }

    // Update Message
    {
        "action": "update",
        "client": "ndtp.dbt.gov.uk",
        "body":
        {
            "topics":
            [
                {
                    "name": "knowledge",
                    "granted_at": "2023-05-05T16:08:32.275858+01:00"
                }
            ]
        }
    }
     */

    @JsonProperty(required = true)
    private String action;

    @JsonProperty(required = true)
    private String client;

    private AccessDetails body;
}
