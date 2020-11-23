/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static net.bytebuddy.matcher.ElementMatchers.any;

import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Interface representing a single type instrumentation. Part of an {@link InstrumentationModule}.
 */
public interface TypeInstrumentation {
  /**
   * A type instrumentation can implement this method to optimize an expensive {@link
   * #typeMatcher()} - usually {@link AgentElementMatchers#implementsInterface(ElementMatcher)} or
   * {@link AgentElementMatchers#extendsClass(ElementMatcher)}. In that case it's useful to check
   * that the classloader contains the class/interface that is being extended.
   *
   * @return A type matcher used to match the classloader under transform
   */
  default ElementMatcher<ClassLoader> classLoaderOptimization() {
    return any();
  }

  /**
   * Returns a type matcher defining which classes should undergo transformations defined by advices
   * returned by {@link #transformers()}.
   */
  ElementMatcher<? super TypeDescription> typeMatcher();

  /**
   * Keys of the returned map are method matchers, values are full names of advice classes that will
   * be applied onto methods that satisfy matcher (key).
   */
  Map<? extends ElementMatcher<? super MethodDescription>, String> transformers();
}
