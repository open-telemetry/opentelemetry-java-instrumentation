/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

class QueryProtocolModelTest extends AbstractQueryProtocolModelTest {
  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @Override
  protected ClientOverrideConfiguration.Builder createClientOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(
            AwsSdkTelemetry.builder(testing.getOpenTelemetry()).build().newExecutionInterceptor());
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }
}
