/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;

abstract class Aws2SqsTracingTest extends AbstractAws2SqsTracingTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  static AwsSdkTelemetry telemetry;

  @Override
  protected final LibraryInstrumentationExtension getTesting() {
    return testing;
  }

  @BeforeEach
  void setup() {
    AwsSdkTelemetryBuilder telemetryBuilder =
        AwsSdkTelemetry.builder(getTesting().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .setMessagingReceiveInstrumentationEnabled(true)
            .setCapturedHeaders(singletonList("test-message-header"));

    configure(telemetryBuilder);
    telemetry = telemetryBuilder.build();
  }

  abstract void configure(AwsSdkTelemetryBuilder telemetryBuilder);

  @Override
  protected ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(telemetry.newExecutionInterceptor());
  }

  @Override
  protected SqsClient configureSqsClient(SqsClient sqsClient) {
    return telemetry.wrap(sqsClient);
  }

  @Override
  protected SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient) {
    return telemetry.wrap(sqsClient);
  }
}
