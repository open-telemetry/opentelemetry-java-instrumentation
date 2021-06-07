/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
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
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(not(isAbstract()))
            .and(named("doExecute"))
            .and(returns(named("com.amazonaws.Response"))),
        RequestExecutorInstrumentation.class.getName() + "$RequestExecutorAdvice");
  }

  @SuppressWarnings("unused")
  public static class RequestExecutorAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.FieldValue("request") Request<?> request,
        @Advice.Return Response<?> response,
        @Advice.Thrown Throwable throwable) {
      if (throwable instanceof Exception) {
        TracingRequestHandler.tracingHandler.afterError(request, response, (Exception) throwable);
      }
      Scope scope = request.getHandlerContext(TracingRequestHandler.SCOPE);
      if (scope != null) {
        scope.close();
      }
    }
  }
}
