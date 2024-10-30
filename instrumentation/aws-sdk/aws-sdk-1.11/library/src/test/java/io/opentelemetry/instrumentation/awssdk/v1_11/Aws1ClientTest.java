/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.client.builder.AwsClientBuilder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class Aws1ClientTest extends AbstractAws1ClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AwsClientBuilder configureClient(AwsClientBuilder client) {
    return client.withRequestHandlers(
        AwsSdkTelemetry.builder(testing().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .build()
            .newRequestHandler());
  }
}
