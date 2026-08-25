/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.Experimental;
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
            .addExecutionInterceptor(telemetry.createExecutionInterceptor())
            .addExecutionInterceptor(telemetry.createExecutionInterceptor())
            .build();

    builder.overrideConfiguration(overrideConfiguration);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);
    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest);

    assertThat(response.messages()).hasSize(1);
    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, false);
  }

  @Test
  void testDisableSqsMessageCreateSpans() {
    assumeTrue(emitStableMessagingSemconv());
    AwsSdkTelemetryBuilder telemetryBuilder =
        AwsSdkTelemetry.builder(getTesting().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true);
    Experimental.setMessageCreateSpansEnabled(telemetryBuilder, false);
    AwsSdkTelemetry disabledTelemetry = telemetryBuilder.build();
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    builder.overrideConfiguration(
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(disabledTelemetry.createExecutionInterceptor())
            .build());

    try (SqsClient client = disabledTelemetry.wrap(builder.build())) {
      client.createQueue(createQueueRequest);
      client.sendMessageBatch(sendMessageBatchRequest);

      getTesting()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("send testSdkSqs")
                              .hasKind(SpanKind.PRODUCER)
                              .hasNoParent()
                              .hasTotalRecordedLinks(0)));
    }
  }
}
