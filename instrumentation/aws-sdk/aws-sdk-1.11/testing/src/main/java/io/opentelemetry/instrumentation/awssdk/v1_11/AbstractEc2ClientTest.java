/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public abstract class AbstractEc2ClientTest extends AbstractBaseAwsClientTest {

  public abstract AmazonEC2ClientBuilder configureClient(AmazonEC2ClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return true;
  }

  @Test
  public void sendRequestWithMockedResponse() throws Exception {
    AmazonEC2ClientBuilder clientBuilder = AmazonEC2ClientBuilder.standard();
    AmazonEC2 client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    String body =
        "<AllocateAddressResponse xmlns=\"http://ec2.amazonaws.com/doc/2016-11-15/\">"
            + "   <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId>"
            + "   <publicIp>192.0.2.1</publicIp>"
            + "   <domain>standard</domain>"
            + "</AllocateAddressResponse>";
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body));

    Object response = client.allocateAddress();
    assertRequestWithMockedResponse(
        response, client, "EC2", "AllocateAddress", "POST", Collections.emptyList());
  }
}
