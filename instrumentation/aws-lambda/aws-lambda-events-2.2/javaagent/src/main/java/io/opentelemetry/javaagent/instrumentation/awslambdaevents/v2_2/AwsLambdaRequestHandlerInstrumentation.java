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
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

public class AwsLambdaRequestHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.services.lambda.runtime.RequestHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.amazonaws.services.lambda.runtime.RequestHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handleRequest"))
            .and(takesArgument(1, named("com.amazonaws.services.lambda.runtime.Context"))),
        AwsLambdaRequestHandlerInstrumentation.class.getName() + "$HandleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleRequestAdvice {

    public static class AdviceScope {
      private final AwsLambdaRequest lambdaRequest;
      private final Scope functionScope;
      private final io.opentelemetry.context.Context functionContext;
      private final Scope messageScope;
      private final io.opentelemetry.context.Context messageContext;

      private AdviceScope(
          AwsLambdaRequest lambdaRequest,
          io.opentelemetry.context.Context functionContext,
          Scope functionScope,
          io.opentelemetry.context.Context messageContext,
          Scope messageScope) {
        this.lambdaRequest = lambdaRequest;
        this.functionContext = functionContext;
        this.functionScope = functionScope;
        this.messageContext = messageContext;
        this.messageScope = messageScope;
      }

      @Nullable
      public static AdviceScope start(Object arg, Context context) {

        Map<String, String> headers = Collections.emptyMap();
        if (arg instanceof APIGatewayProxyRequestEvent) {
          headers = MapUtils.lowercaseMap(((APIGatewayProxyRequestEvent) arg).getHeaders());
        }
        AwsLambdaRequest lambdaRequest = AwsLambdaRequest.create(context, arg, headers);
        io.opentelemetry.context.Context parentContext =
            functionInstrumenter().extract(lambdaRequest);

        if (!functionInstrumenter().shouldStart(parentContext, lambdaRequest)) {
          return null;
        }

        io.opentelemetry.context.Context functionContext =
            functionInstrumenter().start(parentContext, lambdaRequest);
        Scope functionScope = functionContext.makeCurrent();

        io.opentelemetry.context.Context messageContext = null;
        Scope messageScope = null;
        if (arg instanceof SQSEvent) {
          if (AwsLambdaSingletons.messageInstrumenter()
              .shouldStart(functionContext, (SQSEvent) arg)) {
            messageContext =
                AwsLambdaSingletons.messageInstrumenter().start(functionContext, (SQSEvent) arg);
            messageScope = messageContext.makeCurrent();
          }
        }
        return new AdviceScope(
            lambdaRequest, functionContext, functionScope, messageContext, messageScope);
      }

      public void end(Object arg, @Nullable Object result, @Nullable Throwable throwable) {
        if (messageScope != null) {
          messageScope.close();
          AwsLambdaSingletons.messageInstrumenter()
              .end(messageContext, (SQSEvent) arg, null, throwable);
        }
        if (functionScope != null) {
          functionScope.close();
          functionInstrumenter().end(functionContext, lambdaRequest, result, throwable);
        }
        OpenTelemetrySdkAccess.forceFlush(flushTimeout().toNanos(), TimeUnit.NANOSECONDS);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg,
        @Advice.Argument(1) Context context) {
      return AdviceScope.start(arg, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg,
        @Advice.Return @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(arg, result, throwable);
      }
    }
  }
}
