/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.generation;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class AdviceClassNameCollector implements TypeTransformer {
  private final Set<String> adviceClassNames = new HashSet<>();

  @Override
  public void applyAdviceToMethod(
      ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
    adviceClassNames.add(adviceClassName);
  }

  @Override
  public void applyTransformer(AgentBuilder.Transformer transformer) {}

  Set<String> getAdviceClassNames() {
    return adviceClassNames;
  }
}
