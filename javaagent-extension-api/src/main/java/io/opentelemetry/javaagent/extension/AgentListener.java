/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import io.opentelemetry.sdk.autoconfigure.spi.internal.StructuredConfigProperties;
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

  /** Resolve {@link ConfigProperties} from the {@code autoConfiguredOpenTelemetrySdk}. */
  static ConfigProperties resolveConfigProperties(
      AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ConfigProperties sdkConfigProperties =
        AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk);
    if (sdkConfigProperties != null) {
      return sdkConfigProperties;
    }
    StructuredConfigProperties structuredConfigProperties =
        AutoConfigureUtil.getStructuredConfig(autoConfiguredOpenTelemetrySdk);
    if (structuredConfigProperties != null) {
      return new StructuredConfigPropertiesBridge(structuredConfigProperties);
    }
    // Should never happen
    throw new IllegalStateException(
        "AutoConfiguredOpenTelemetrySdk does not have ConfigProperties or StructuredConfigProperties. This is likely a programming error in opentelemetry-java");
  }
}
