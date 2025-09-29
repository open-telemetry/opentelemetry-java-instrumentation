/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2.AwsLambdaSingletons.flushTimeout;
import static io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2.AwsLambdaSingletons.functionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.amazonaws.services.lambda.runtime.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AwsLambdaRequestStreamHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.services.lambda.runtime.RequestStreamHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.amazonaws.services.lambda.runtime.RequestStreamHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handleRequest"))
            .and(takesArgument(2, named("com.amazonaws.services.lambda.runtime.Context"))),
        AwsLambdaRequestStreamHandlerInstrumentation.class.getName() + "$HandleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleRequestAdvice {

    public static class AdviceScope {
      private final AwsLambdaRequest lambdaRequest;
      private final io.opentelemetry.context.Context context;
      private final Scope scope;

      private AdviceScope(
          AwsLambdaRequest lambdaRequest,
          io.opentelemetry.context.Context otelContext,
          Scope scope) {
        this.lambdaRequest = lambdaRequest;
        this.context = otelContext;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Object arg, Context context) {
        AwsLambdaRequest lambdaRequest =
            AwsLambdaRequest.create(context, arg, Collections.emptyMap());
        io.opentelemetry.context.Context parentContext =
            functionInstrumenter().extract(lambdaRequest);
        if (!functionInstrumenter().shouldStart(parentContext, lambdaRequest)) {
          return null;
        }

        io.opentelemetry.context.Context otelContext =
            functionInstrumenter().start(parentContext, lambdaRequest);
        return new AdviceScope(lambdaRequest, otelContext, otelContext.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        functionInstrumenter().end(context, lambdaRequest, null, throwable);
        OpenTelemetrySdkAccess.forceFlush(flushTimeout().toNanos(), TimeUnit.NANOSECONDS);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(0) InputStream input, @Advice.Argument(2) Context context) {
      return AdviceScope.start(input, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
