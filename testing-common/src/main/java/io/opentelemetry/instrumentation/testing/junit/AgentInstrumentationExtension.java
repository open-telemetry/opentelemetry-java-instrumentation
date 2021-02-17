/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import io.opentelemetry.instrumentation.testing.AgentTestRunner;

/**
 * JUnit 5 extension for writing javaagent instrumentation tests.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MyAgentInstrumentationTest {
 *     {@literal @}RegisterExtension
 *     static final AgentInstrumentationExtension instrTesting = AgentInstrumentationExtension.create();
 *
 *     {@literal @}Test
 *     void test() {
 *       // test code ...
 *
 *       var spans = instrTesting.spans();
 *       // assertions on collected spans ...
 *     }
 *   }
 * </pre>
 *
 * <p>Note that {@link AgentInstrumentationExtension} will not work by itself, you have to run the
 * tests process with the {@code agent-for-testing} javaagent.
 */
public final class AgentInstrumentationExtension extends InstrumentationExtension {
  private AgentInstrumentationExtension() {
    super(AgentTestRunner.instance());
  }

  public static AgentInstrumentationExtension create() {
    return new AgentInstrumentationExtension();
  }
}
