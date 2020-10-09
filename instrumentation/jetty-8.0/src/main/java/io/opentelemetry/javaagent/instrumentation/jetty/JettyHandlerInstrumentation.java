/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jetty;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JettyHandlerInstrumentation extends Instrumenter.Default {

  public JettyHandlerInstrumentation() {
    super("jetty", "jetty-8");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.eclipse.jetty.server.Handler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(named("org.eclipse.jetty.server.handler.HandlerWrapper"))
        .and(implementsInterface(named("org.eclipse.jetty.server.Handler")));
  }

  @Override
  public String[] helperClassNames() {
    // order matters here because subclasses (e.g. JettyHttpServerTracer) need to be injected into
    // the class loader after their super classes (e.g. Servlet3HttpServerTracer)
    return new String[] {
      "io.opentelemetry.instrumentation.servlet.HttpServletRequestGetter",
      "io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer",
      "io.opentelemetry.instrumentation.auto.servlet.v3_0.Servlet3HttpServerTracer",
      "io.opentelemetry.instrumentation.auto.servlet.v3_0.TagSettingAsyncListener",
      packageName + ".JettyHttpServerTracer",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("handle")
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("org.eclipse.jetty.server.Request")))
            .and(takesArgument(2, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(3, named("javax.servlet.http.HttpServletResponse")))
            .and(isPublic()),
        packageName + ".JettyHandlerAdvice");
  }
}
