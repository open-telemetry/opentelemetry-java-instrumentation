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

package io.opentelemetry.instrumentation.auto.apachehttpasyncclient;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.Header;
import org.apache.http.HttpRequest;

/**
 * Early versions don't copy headers over on redirect. This instrumentation copies our headers over
 * manually. Inspired by
 * https://github.com/elastic/apm-agent-java/blob/master/apm-agent-plugins/apm-apache-httpclient-plugin/src/main/java/co/elastic/apm/agent/httpclient/ApacheHttpAsyncClientRedirectInstrumentation.java
 */
@AutoService(Instrumenter.class)
public class ApacheHttpClientRedirectInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientRedirectInstrumentation() {
    super("httpasyncclient", "apache-httpasyncclient");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.apache.http.client.RedirectStrategy");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.http.client.RedirectStrategy"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("getRedirect"))
            .and(takesArgument(0, named("org.apache.http.HttpRequest"))),
        ApacheHttpClientRedirectInstrumentation.class.getName() + "$ClientRedirectAdvice");
  }

  public static class ClientRedirectAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    private static void onAfterExecute(
        @Advice.Argument(0) final HttpRequest original,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) final HttpRequest redirect) {
      if (redirect == null) {
        return;
      }
      // TODO this only handles W3C headers
      // Apache HttpClient 4.0.1+ copies headers from original to redirect only
      // if redirect headers are empty. Because we add headers
      // "traceparent" and "tracestate" to redirect: it means redirect headers never
      // will be empty. So in case if not-instrumented redirect had no headers,
      // we just copy all not set headers from original to redirect (doing same
      // thing as apache httpclient does).
      if (!redirect.headerIterator().hasNext()) {
        // redirect didn't have other headers besides tracing, so we need to do copy
        // (same work as Apache HttpClient 4.0.1+ does w/o instrumentation)
        redirect.setHeaders(original.getAllHeaders());
      } else {
        for (Header header : original.getAllHeaders()) {
          String name = header.getName().toLowerCase();
          if (name.equals("traceparent") || name.equals("tracestate")) {
            if (!redirect.containsHeader(header.getName())) {
              redirect.setHeader(header.getName(), header.getValue());
            }
          }
        }
      }
    }
  }
}
