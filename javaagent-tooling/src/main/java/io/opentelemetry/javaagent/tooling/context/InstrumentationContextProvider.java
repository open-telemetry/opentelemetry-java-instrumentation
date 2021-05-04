/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.context;

import net.bytebuddy.agent.builder.AgentBuilder;

public interface InstrumentationContextProvider {

  /**
   * Hook to provide an agent builder after advice is applied to target class. Used to implement
   * context-store lookup.
   */
  AgentBuilder.Identified.Extendable instrumentationTransformer(
      AgentBuilder.Identified.Extendable builder);

  /** Hook to define additional instrumentation. Run at instrumentation advice is hooked up. */
  AgentBuilder.Identified.Extendable additionalInstrumentation(
      AgentBuilder.Identified.Extendable builder);
}
