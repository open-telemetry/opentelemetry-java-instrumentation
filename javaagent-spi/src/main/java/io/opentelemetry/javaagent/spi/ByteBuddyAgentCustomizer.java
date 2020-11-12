/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * {@link ByteBuddyAgentCustomizer} customizes ByteBuddy agent builder right before the agent is
 * installed - {@link AgentBuilder#installOn(Instrumentation)}. This SPI can be used to customize
 * {@link AgentBuilder} for vendor specific needs. For example install custom listeners or exclude
 * classes. Use this SPI carefully because it can change {@link net.bytebuddy.ByteBuddy} behaviour.
 *
 * <p>This is a service provider interface that requires implementations to be registered in {@code
 * META-INF/services} folder.
 */
public interface ByteBuddyAgentCustomizer {

  /**
   * Customize ByteBuddy agent builder before {@link AgentBuilder#installOn(Instrumentation)} is
   * called.
   *
   * @param agentBuilder ByteBuddy agent from {@code AgentInstaller}.
   * @return customized agent builder.
   */
  AgentBuilder customize(AgentBuilder agentBuilder);
}
