/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class Aws2SqsDefaultPropagatorTest extends Aws2SqsTracingTest {

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {}

  @Override
  protected boolean isSqsAttributeInjectionEnabled() {
    return false;
  }

  @Test
  void testDuplicateTracingInterceptor() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    ClientOverrideConfiguration overrideConfiguration =
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(telemetry.newExecutionInterceptor())
            .addExecutionInterceptor(telemetry.newExecutionInterceptor())
            .build();

    builder.overrideConfiguration(overrideConfiguration);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);
    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest);

    assertThat(response.messages().size()).isEqualTo(1);
    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, false);
  }
}
