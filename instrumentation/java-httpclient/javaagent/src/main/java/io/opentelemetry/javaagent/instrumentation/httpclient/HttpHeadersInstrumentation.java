/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentSpan;
import static io.opentelemetry.javaagent.instrumentation.httpclient.JdkHttpClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.net.http.HttpHeaders;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpHeadersInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(nameStartsWith("jdk.internal."))
        .and(extendsClass(named("java.net.http.HttpRequest")));
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
      // TODO (trask) need to store Context into HttpHeaders after startOperation, and only inject
      //  if that Context is present, otherwise we may end up injecting even if the http client span
      //  was suppressed (e.g. some higher level http client is using Java HttpClient)
      if (currentSpan().isRecording()) {
        headers = tracer().inject(headers);
      }
    }
  }
}
