/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * {@link AgentListener} can be used to execute code before/after Java agent installation, for
 * example to install any implementation providers that are used by instrumentations. For instance,
 * this project uses this SPI to install OpenTelemetry SDK.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface AgentListener extends Ordered {

  /**
   * Runs before the {@link AgentBuilder} construction, before any instrumentation is added.
   *
   * <p>Execute only a minimal code because any classes loaded before the agent installation will
   * have to be retransformed, which takes extra time, and more importantly means that fields can't
   * be added to those classes - which causes {@link VirtualField} to fall back to the less
   * performant {@link Cache} implementation for those classes.
   */
  default void beforeAgent(Config config) {}

  /**
   * Runs after instrumentations are added to {@link AgentBuilder} and after the agent is installed
   * on an {@link Instrumentation}.
   */
  default void afterAgent(Config config) {}
}
