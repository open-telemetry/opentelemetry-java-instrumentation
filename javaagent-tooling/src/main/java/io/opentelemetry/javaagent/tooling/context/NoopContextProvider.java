/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.context;

import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;

public class NoopContextProvider implements InstrumentationContextProvider {

  public static final NoopContextProvider INSTANCE = new NoopContextProvider();

  private NoopContextProvider() {}

  @Override
  public Extendable instrumentationTransformer(Extendable builder) {
    return builder;
  }

  @Override
  public Extendable additionalInstrumentation(Extendable builder) {
    return builder;
  }
}
