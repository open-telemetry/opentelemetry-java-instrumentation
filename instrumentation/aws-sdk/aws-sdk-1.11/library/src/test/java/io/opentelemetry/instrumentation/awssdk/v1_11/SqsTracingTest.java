/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonList;

import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqsTracingTest extends AbstractSqsTracingTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client) {
    AwsSdkTelemetryBuilder telemetryBuilder =
        AwsSdkTelemetry.builder(testing().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .setCapturedHeaders(singletonList("Test-Message-Header"));
    if (!emitStableMessagingSemconv()) {
      telemetryBuilder.setMessagingReceiveTelemetryEnabled(true);
    }
    return client.withRequestHandlers(telemetryBuilder.build().createRequestHandler());
  }
}
