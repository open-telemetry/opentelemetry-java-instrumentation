/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.awslambda.v1_0;

import static io.opentelemetry.instrumentation.auto.awslambda.v1_0.AwsLambdaInstrumentationHelper.FUNCTION_TRACER;
import static io.opentelemetry.instrumentation.auto.awslambda.v1_0.AwsLambdaInstrumentationHelper.MESSAGE_TRACER;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class AwsLambdaRequestHandlerInstrumentation extends AbstractAwsLambdaInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
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
        @Advice.Local("otelFunctionSpan") Span functionSpan,
        @Advice.Local("otelFunctionScope") Scope functionScope,
        @Advice.Local("otelMessageSpan") Span messageSpan,
        @Advice.Local("otelMessageScope") Scope messageScope) {
      functionSpan = FUNCTION_TRACER.startSpan(context, Kind.SERVER);
      functionScope = FUNCTION_TRACER.startScope(functionSpan);
      if (arg instanceof SQSEvent) {
        messageSpan = MESSAGE_TRACER.startSpan(context, (SQSEvent) arg);
        messageScope = MESSAGE_TRACER.startScope(messageSpan);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelFunctionSpan") Span functionSpan,
        @Advice.Local("otelFunctionScope") Scope functionScope,
        @Advice.Local("otelMessageSpan") Span messageSpan,
        @Advice.Local("otelMessageScope") Scope messageScope) {

      if (messageScope != null) {
        messageScope.close();
        if (throwable != null) {
          MESSAGE_TRACER.endExceptionally(messageSpan, throwable);
        } else {
          MESSAGE_TRACER.end(messageSpan);
        }
      }

      functionScope.close();
      if (throwable != null) {
        FUNCTION_TRACER.endExceptionally(functionSpan, throwable);
      } else {
        FUNCTION_TRACER.end(functionSpan);
      }
      OpenTelemetrySdkAccess.forceFlush(1, TimeUnit.SECONDS);
    }
  }
}
