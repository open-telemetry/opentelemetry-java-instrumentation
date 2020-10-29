/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Interface representing a single type instrumentation. Part of an {@link InstrumentationModule}.
 */
public interface TypeInstrumentation {

  /**
   * @return A type matcher defining which classes should undergo transformations defined by advices
   *     returned by {@link #transformers()}.
   */
  ElementMatcher<? super TypeDescription> typeMatcher();

  /**
   * @return Keys of the returned map are method matchers, values are full names of advice classes
   *     that will be applied onto methods that satisfy matcher (key).
   */
  Map<? extends ElementMatcher<? super MethodDescription>, String> transformers();
}
