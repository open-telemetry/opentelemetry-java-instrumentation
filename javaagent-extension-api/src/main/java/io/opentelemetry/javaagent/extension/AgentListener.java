/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * {@link AgentListener} can be used to execute code after Java agent installation. It can be used
 * to install additional instrumentation that does not depend on bytecode injection, e.g. JMX
 * listeners. Can also be used to obtain the {@link AutoConfiguredOpenTelemetrySdk}.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface AgentListener extends Ordered {

  /**
   * Runs after instrumentations are added to {@link AgentBuilder} and after the agent is installed
   * on an {@link Instrumentation}.
   */
  void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk);
}
