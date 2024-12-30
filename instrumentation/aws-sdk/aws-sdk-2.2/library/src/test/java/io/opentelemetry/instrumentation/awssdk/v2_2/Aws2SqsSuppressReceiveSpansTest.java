/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;

public abstract class Aws2SqsSuppressReceiveSpansTest
    extends AbstractAws2SqsSuppressReceiveSpansTest {
  protected AwsSdkTelemetry telemetry;

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected SqsClient configureSqsClient(SqsClient sqsClient) {
    return telemetry.wrap(sqsClient);
  }

  @Override
  protected SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient) {
    return telemetry.wrap(sqsClient);
  }

  @Override
  protected ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(telemetry.newExecutionInterceptor());
  }

  protected abstract void configure(AwsSdkTelemetryBuilder telemetryBuilder);

  @BeforeEach
  void setup() {
    AwsSdkTelemetryBuilder telemetryBuilder =
        AwsSdkTelemetry.builder(getTesting().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true);
    configure(telemetryBuilder);
    telemetry = telemetryBuilder.build();
  }
}
