/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JettyContentLengthInstrumentation implements TypeInstrumentation {
  private static final String[] RESPONSE_CLASSES = {
    "org.eclipse.jetty.server.Response",
    "org.eclipse.jetty.ee9.nested.Response",
    "org.eclipse.jetty.ee10.servlet.ServletApiResponse"
  };

  private final String adviceClassName;

  public JettyContentLengthInstrumentation(String adviceClassName) {
    this.adviceClassName = adviceClassName;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.eclipse.jetty.server.Response");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(RESPONSE_CLASSES);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("setContentLength", "setContentLengthLong")
            .and(takesArguments(1))
            .or(
                namedOneOf("setHeader", "addHeader", "setIntHeader", "addIntHeader")
                    .and(takesArguments(2))),
        adviceClassName);
  }
}
