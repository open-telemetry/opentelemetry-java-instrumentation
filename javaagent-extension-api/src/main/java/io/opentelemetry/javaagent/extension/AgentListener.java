/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
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
   *
   * @deprecated Implement {{@link #afterAgent(AutoConfiguredOpenTelemetrySdk)}} instead.
   */
  @Deprecated
  default void afterAgent(
      Config config, AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in a future release;"
            + " implement AgentListener#afterAgent(AutoConfiguredOpenTelemetrySdk) instead");
  }

  /**
   * Runs after instrumentations are added to {@link AgentBuilder} and after the agent is installed
   * on an {@link Instrumentation}.
   */
  default void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    afterAgent(Config.get(), autoConfiguredOpenTelemetrySdk);
  }
}
