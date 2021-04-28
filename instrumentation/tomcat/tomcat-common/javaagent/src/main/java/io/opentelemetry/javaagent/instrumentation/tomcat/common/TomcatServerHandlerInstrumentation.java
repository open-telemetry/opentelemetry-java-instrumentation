/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TomcatServerHandlerInstrumentation implements TypeInstrumentation {
  private final String adviceClassName;
  private final ElementMatcher<TypeDescription> requestMatcher;

  public TomcatServerHandlerInstrumentation(
      String adviceClassName, ElementMatcher<TypeDescription> requestMatcher) {
    this.adviceClassName = adviceClassName;
    this.requestMatcher = requestMatcher;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.coyote.Adapter"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("service"))
            .and(takesArgument(0, requestMatcher))
            .and(takesArgument(1, named("org.apache.coyote.Response"))),
        adviceClassName);
  }
}
