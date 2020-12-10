/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta.OPERATION_SCOPE_PAIR_KEY;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.amazonaws.Request;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Due to a change in the AmazonHttpClient class, this instrumentation is needed to support newer
 * versions. The {@link AwsHttpClientInstrumentation} class should cover older versions.
 */
public class RequestExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.amazonaws.http.AmazonHttpClient$RequestExecutor");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(not(isAbstract())).and(named("doExecute")),
        RequestExecutorInstrumentation.class.getName() + "$RequestExecutorAdvice");
  }

  public static class RequestExecutorAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.FieldValue("request") Request<?> request, @Advice.Thrown Throwable throwable) {
      if (throwable != null) {
        OperationScopePair scope = request.getHandlerContext(OPERATION_SCOPE_PAIR_KEY);
        if (scope != null) {
          request.addHandlerContext(OPERATION_SCOPE_PAIR_KEY, null);
          scope.getOperation().endExceptionally(throwable);
          scope.closeScope();
        }
      }
    }
  }
}
