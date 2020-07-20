/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.jetty;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
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
      "io.opentelemetry.auto.instrumentation.servlet.v3_0.Servlet3HttpServerTracer",
      "io.opentelemetry.auto.instrumentation.servlet.v3_0.TagSettingAsyncListener",
      "io.opentelemetry.auto.instrumentation.servlet.v3_0.TagSettingAsyncListener",
      "io.opentelemetry.auto.instrumentation.servlet.v3_0.CountingHttpServletResponse",
      "io.opentelemetry.auto.instrumentation.servlet.v3_0.CountingHttpServletResponse$CountingServletOutputStream",
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
