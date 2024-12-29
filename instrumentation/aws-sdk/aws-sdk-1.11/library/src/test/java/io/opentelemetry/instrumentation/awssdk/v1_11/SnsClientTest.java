/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class SnsClientTest extends AbstractSnsClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AmazonSNSClientBuilder configureClient(AmazonSNSClientBuilder clientBuilder) {
    return clientBuilder.withRequestHandlers(
        AwsSdkTelemetry.builder(testing().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .build()
            .newRequestHandler());
  }
}
