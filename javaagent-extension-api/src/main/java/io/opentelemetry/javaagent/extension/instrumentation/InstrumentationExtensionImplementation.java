/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

import java.util.Iterator;
import java.util.ServiceLoader;
import net.bytebuddy.agent.builder.AgentBuilder;

abstract class InstrumentationExtensionImplementation {
  private static final InstrumentationExtensionImplementation INSTANCE;

  static {
    Iterator<InstrumentationExtensionImplementation> impls =
        ServiceLoader.load(InstrumentationExtensionImplementation.class).iterator();
    if (!impls.hasNext()) {
      throw new IllegalStateException("The application is running without OpenTelemetry javaagent");
    }
    INSTANCE = impls.next();
  }

  static InstrumentationExtensionImplementation get() {
    return INSTANCE;
  }

  abstract AgentBuilder extend(
      InstrumentationModule instrumentationModule, AgentBuilder parentAgentBuilder);
}
