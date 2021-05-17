/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This interface represents type transformations that can be applied to a type instrumented using
 * {@link TypeInstrumentation}.
 *
 * <p>This interface should not be implemented by the javaagent extension developer - the javaagent
 * will provide the implementation of all transformations described here.
 */
public interface TypeTransformer {
  /**
   * Apply the advice class named {@code adviceClassName} to the instrumented type methods that
   * match {@code methodMatcher}.
   */
  void applyAdviceToMethod(
      ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName);

  /**
   * Apply a custom ByteBuddy {@link AgentBuilder.Transformer} to the instrumented type. Note that
   * since this is a completely custom transformer, muzzle won't be able to scan for references or
   * helper classes.
   */
  void applyTransformer(AgentBuilder.Transformer transformer);
}
