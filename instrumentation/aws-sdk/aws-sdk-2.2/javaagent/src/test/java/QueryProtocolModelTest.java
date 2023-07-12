/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractQueryProtocolModelTest;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

public class QueryProtocolModelTest extends AbstractQueryProtocolModelTest {
  private final AgentInstrumentationExtension extension = AgentInstrumentationExtension.create();

  @Override
  protected ClientOverrideConfiguration.Builder createClientOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(
            AwsSdkTelemetry.builder(extension.getOpenTelemetry())
                .build()
                .newExecutionInterceptor());
  }

  @Override
  protected InstrumentationExtension getInstrumentationExtension() {
    return extension;
  }
}
