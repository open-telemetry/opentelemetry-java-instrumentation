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

package io.opentelemetry.instrumentation.auto.httpclient;

import static io.opentelemetry.instrumentation.auto.httpclient.JdkHttpClientTracer.TRACER;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

@AutoService(Instrumenter.class)
public class HttpHeadersInstrumentation extends Instrumenter.Default {

  public HttpHeadersInstrumentation() {
    super("httpclient");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(HttpClient.class.getName(), State.class.getName());
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(ElementMatchers.<TypeDescription>nameStartsWith("jdk.internal."))
        // .and(not(named("jdk.internal.net.http.ImmutableHttpRequest")))
        .and(extendsClass(named("java.net.http.HttpRequest")));

    // return named("java.net.http.HttpHeaders");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpHeadersInjectAdapter", packageName + ".JdkHttpClientTracer"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("headers")),
        HttpHeadersInstrumentation.class.getName() + "$HeadersAdvice");
  }

  public static class HeadersAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) HttpHeaders headers) {
      if (TRACER.getCurrentSpan().isRecording()) {
        headers = TRACER.inject(headers);
      }
    }
  }
}
