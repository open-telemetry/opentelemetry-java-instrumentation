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
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Interface representing a single type instrumentation. Part of an {@link InstrumentationModule}.
 *
 * <p>Classes implementing {@link TypeInstrumentation} should be public and non-final so that it's
 * possible to extend and reuse them in vendor distributions.
 */
public interface TypeInstrumentation {
  /**
   * An optimization to short circuit matching in the case where the instrumented library is not
   * even present on the class path.
   *
   * <p>Most applications have only a small subset of libraries on their class path, so this ends up
   * being a very useful optimization.
   *
   * <p>Some background on type matcher performance:
   *
   * <p>Type matchers that only match against the type name are fast, e.g. {@link
   * ElementMatchers#named(String)}.
   *
   * <p>All other type matchers require some level of bytecode inspection, e.g. {@link
   * ElementMatchers#isAnnotatedWith(ElementMatcher)}.
   *
   * <p>Type matchers that need to inspect the super class hierarchy are even more expensive, e.g.
   * {@link AgentElementMatchers#implementsInterface(ElementMatcher)}. This is because they require
   * inspecting multiple super classes/interfaces as well (which may not even be loaded yet in which
   * case their bytecode has to be read and inspected).
   *
   * @return A type matcher that rejects classloaders that do not contain desired interfaces or base
   *     classes.
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
