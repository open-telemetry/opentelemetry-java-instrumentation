/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TomcatResponseFacadeInstrumentation implements TypeInstrumentation {
  private static final String RESPONSE_FACADE = "org.apache.catalina.connector.ResponseFacade";

  private final String adviceClassName;

  public TomcatResponseFacadeInstrumentation(String adviceClassName) {
    this.adviceClassName = adviceClassName;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(RESPONSE_FACADE);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(RESPONSE_FACADE);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("setHeader", "addHeader")
            .and(takesArguments(String.class, String.class))
            .or(
                namedOneOf("setIntHeader", "addIntHeader")
                    .and(takesArguments(String.class, int.class)))
            .or(named("setContentLength").and(takesArguments(int.class)))
            .or(named("setContentLengthLong").and(takesArguments(long.class)))
            .and(isPublic()),
        adviceClassName);
  }
}
