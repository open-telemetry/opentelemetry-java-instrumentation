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

package io.opentelemetry.instrumentation.auto.servlet.v3_1;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class Servlet31Instrumentation extends Instrumenter.Default {
  public Servlet31Instrumentation() {
    super("servlet", "servlet-3.1");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // WriteListener was added in servlet-api 3.1, 3.0 does not have it
    return hasClassesNamed("javax.servlet.http.HttpServlet", "javax.servlet.WriteListener");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        namedOneOf("javax.servlet.FilterChain", "javax.servlet.http.HttpServlet"));
  }

  @Override
  public String[] helperClassNames() {
    String commonPackageName = "io.opentelemetry.instrumentation.auto.servlet.v3";
    return new String[] {
      "io.opentelemetry.instrumentation.servlet.HttpServletRequestGetter",
      "io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer",
      commonPackageName + ".CountingHttpServletRequest",
      commonPackageName + ".AbstractCountingHttpServletResponse",
      commonPackageName + ".AbstractCountingHttpServletResponse$CountingPrintWriter",
      commonPackageName + ".Servlet3HttpServerTracer",
      commonPackageName + ".TagSettingAsyncListener",
      packageName + ".Servlet31CountingHttpServletResponse",
      packageName + ".Servlet31CountingHttpServletResponse$CountingServletOutputStream",
    };
  }

  /**
   * Here we are instrumenting the public method for HttpServlet. This should ensure that this
   * advice is always called before HttpServletInstrumentation which is instrumenting the protected
   * method.
   */
  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        namedOneOf("doFilter", "service")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        packageName + ".Servlet31Advice");
  }
}
