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
   * #typeMatcher()} - usually one that uses {@link AgentElementMatchers}, e.g. {@link
   * AgentElementMatchers#implementsInterface(ElementMatcher)} or {@link
   * AgentElementMatchers#extendsClass(ElementMatcher)}. Type matchers that check annotation
   * presence or class inheritance are particularly expensive for classloaders that do not contain
   * the base class/interface/annotation. To make this check significantly less expensive this
   * method can be used to verify that the classloader contains the class/interface that is being
   * extended.
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
