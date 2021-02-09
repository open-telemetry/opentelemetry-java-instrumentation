/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambda.v1_0;

import static io.opentelemetry.javaagent.instrumentation.awslambda.v1_0.AwsLambdaInstrumentationHelper.functionTracer;
import static io.opentelemetry.javaagent.instrumentation.awslambda.v1_0.AwsLambdaInstrumentationHelper.messageTracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

public class AwsLambdaRequestHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.services.lambda.runtime.RequestHandler");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return implementsInterface(named("com.amazonaws.services.lambda.runtime.RequestHandler"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("handleRequest"))
            .and(takesArgument(1, named("com.amazonaws.services.lambda.runtime.Context"))),
        AwsLambdaRequestHandlerInstrumentation.class.getName() + "$HandleRequestAdvice");
  }

  public static class HandleRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg,
        @Advice.Argument(1) Context context,
        @Advice.Local("otelFunctionContext") io.opentelemetry.context.Context functionContext,
        @Advice.Local("otelFunctionScope") Scope functionScope,
        @Advice.Local("otelMessageContext") io.opentelemetry.context.Context messageContext,
        @Advice.Local("otelMessageScope") Scope messageScope) {
      functionContext = functionTracer().startSpan(context, SpanKind.SERVER, arg);
      functionScope = functionContext.makeCurrent();
      if (arg instanceof SQSEvent) {
        messageContext = messageTracer().startSpan((SQSEvent) arg);
        messageScope = messageContext.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelFunctionContext") io.opentelemetry.context.Context functionContext,
        @Advice.Local("otelFunctionScope") Scope functionScope,
        @Advice.Local("otelMessageContext") io.opentelemetry.context.Context messageContext,
        @Advice.Local("otelMessageScope") Scope messageScope) {

      if (messageScope != null) {
        messageScope.close();
        if (throwable != null) {
          messageTracer().endExceptionally(messageContext, throwable);
        } else {
          messageTracer().end(messageContext);
        }
      }

      functionScope.close();
      if (throwable != null) {
        functionTracer().endExceptionally(functionContext, throwable);
      } else {
        functionTracer().end(functionContext);
      }
      OpenTelemetrySdkAccess.forceFlush(1, TimeUnit.SECONDS);
    }
  }
}
