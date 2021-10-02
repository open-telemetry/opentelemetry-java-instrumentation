/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.javaagent.extension.Ordered;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * An {@link AgentExtension} provides a way to modify/enrich the OpenTelemetry Javaagent behavior.
 * It can be an {@link InstrumentationModule} or a completely custom implementation. Because an
 * extension can heavily modify the javaagent's behavior extreme caution is advised.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface AgentExtension extends Ordered {

  /**
   * Extend the passed {@code agentBuilder} with custom logic (e.g. instrumentation).
   *
   * @return The customized agent. Note that this method MUST return a non-null {@link AgentBuilder}
   *     instance that contains all customizations defined in this extension.
   */
  AgentBuilder extend(AgentBuilder agentBuilder);

  /**
   * Returns the name of the extension. It does not have to be unique, but it should be
   * human-readable: javaagent uses the extension name in its logs.
   */
  String extensionName();
}
