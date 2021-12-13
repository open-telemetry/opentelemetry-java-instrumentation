/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * {@link AgentListener} can be used to execute code before/after Java agent installation, for
 * example to install any implementation providers that are used by instrumentations. Can also be
 * used to obtain the {@link AutoConfiguredOpenTelemetrySdk}.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface AgentListener extends Ordered {

  /**
   * Runs before {@link AgentBuilder} construction, before any instrumentation is added.
   *
   * <p>Execute only minimal code because any classes loaded before the agent installation will have
   * to be retransformed, which takes extra time, and more importantly means that fields can't be
   * added to those classes - which causes {@link VirtualField} to fall back to the less performant
   * {@link Cache} implementation for those classes.
   *
   * @deprecated Use {@link #beforeAgent(Config, AutoConfiguredOpenTelemetrySdk)}
   */
  @Deprecated
  default void beforeAgent(Config config) {}

  /**
   * Runs before {@link AgentBuilder} construction, before any instrumentation is added. Not called
   * if noop api enabled via {@code otel.javaagent.experimental.use-noop-api}.
   *
   * <p>Execute only minimal code because any classes loaded before the agent installation will have
   * to be retransformed, which takes extra time, and more importantly means that fields can't be
   * added to those classes - which causes {@link VirtualField} to fall back to the less performant
   * {@link Cache} implementation for those classes.
   */
  default void beforeAgent(
      Config config, AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {}

  /**
   * Runs after instrumentations are added to {@link AgentBuilder} and after the agent is installed
   * on an {@link Instrumentation}.
   *
   * @deprecated Use {@link #afterAgent(Config, AutoConfiguredOpenTelemetrySdk)}
   */
  @Deprecated
  default void afterAgent(Config config) {}

  /**
   * Runs after instrumentations are added to {@link AgentBuilder} and after the agent is installed
   * on an {@link Instrumentation}. Not called if noop api enabled via {@code
   * otel.javaagent.experimental.use-noop-api}.
   */
  default void afterAgent(
      Config config, AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {}
}
