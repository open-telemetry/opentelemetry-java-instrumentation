/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.web.v6_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientUrlTemplate;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.lang.Nullable;

public class RestTemplateInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.client.RestTemplate");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doExecute").and(takesArgument(1, String.class)),
        this.getClass().getName() + "$UrlTemplateAdvice");
  }

  @SuppressWarnings("unused")
  public static class UrlTemplateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(@Advice.Argument(1) String uriTemplate) {
      if (uriTemplate != null) {
        String path = UrlParser.getPath(uriTemplate);
        if (path != null) {
          return HttpClientUrlTemplate.with(Java8BytecodeBridge.currentContext(), path);
        }
      }
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
