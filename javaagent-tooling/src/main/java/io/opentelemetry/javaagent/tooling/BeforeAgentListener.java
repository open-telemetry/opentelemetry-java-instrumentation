/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Internal listener SPI that runs before the instrumentation is installed.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface BeforeAgentListener extends Ordered {

  /** Runs before {@link AgentBuilder} construction, before any instrumentation is added. */
  void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk);
}
