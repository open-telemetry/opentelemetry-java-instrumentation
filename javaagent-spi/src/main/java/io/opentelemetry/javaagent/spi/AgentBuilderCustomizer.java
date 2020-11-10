/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * {@link AgentBuilderCustomizer} customizes ByteBuddy agent builder right before {@link
 * AgentBuilder#installOn(Instrumentation)} is called.
 *
 * <p>This is a service provider interface that requires to register implementation in
 * `META-INF/services` folder.
 */
public interface AgentBuilderCustomizer {

  /**
   * Customize ByteBuddy agent builder before {@link AgentBuilder#installOn(Instrumentation)} is
   * called.
   *
   * @param agentBuilder ByteBuddy agent from {@code AgentInstaller}.
   * @return
   */
  AgentBuilder customize(AgentBuilder agentBuilder);
}
