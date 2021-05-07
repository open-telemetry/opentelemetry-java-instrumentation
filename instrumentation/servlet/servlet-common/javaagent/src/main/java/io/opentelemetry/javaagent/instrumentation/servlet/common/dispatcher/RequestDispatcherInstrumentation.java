/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.dispatcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RequestDispatcherInstrumentation implements TypeInstrumentation {
  private final String basePackageName;
  private final String adviceClassName;

  public RequestDispatcherInstrumentation(String basePackageName, String adviceClassName) {
    this.basePackageName = basePackageName;
    this.adviceClassName = adviceClassName;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(basePackageName + ".RequestDispatcher");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named(basePackageName + ".RequestDispatcher"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        namedOneOf("forward", "include")
            .and(takesArguments(2))
            .and(takesArgument(0, named(basePackageName + ".ServletRequest")))
            .and(takesArgument(1, named(basePackageName + ".ServletResponse")))
            .and(isPublic()),
        adviceClassName);
  }
}
