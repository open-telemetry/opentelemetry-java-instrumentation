/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2ClientRecordHttpErrorTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

public class Aws2ClientRecordHttpErrorTest extends AbstractAws2ClientRecordHttpErrorTest {
  @RegisterExtension
  private final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  public ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }
}
