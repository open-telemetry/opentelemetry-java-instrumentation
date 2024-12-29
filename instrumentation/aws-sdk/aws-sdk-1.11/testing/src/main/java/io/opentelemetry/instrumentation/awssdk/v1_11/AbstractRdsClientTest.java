/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClientBuilder;
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public abstract class AbstractRdsClientTest extends AbstractBaseAwsClientTest {

  public abstract AmazonRDSClientBuilder configureClient(AmazonRDSClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return true;
  }

  @Test
  public void sendRequestWithMockedResponse() throws Exception {
    AmazonRDSClientBuilder clientBuilder = AmazonRDSClientBuilder.standard();
    AmazonRDS client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    String body =
        "<DeleteOptionGroupResponse xmlns=\"http://rds.amazonaws.com/doc/2014-09-01/\">"
            + "    <ResponseMetadata>"
            + "        <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>"
            + "    </ResponseMetadata>"
            + "</DeleteOptionGroupResponse>";
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body));

    Object response = client.deleteOptionGroup(new DeleteOptionGroupRequest());
    assertRequestWithMockedResponse(
        response, client, "RDS", "DeleteOptionGroup", "POST", Collections.emptyList());
  }
}
