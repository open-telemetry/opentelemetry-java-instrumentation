/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0.AwsLambdaInstrumentationHelper.flushTimeout;
import static io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0.AwsLambdaInstrumentationHelper.functionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
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
    return implementsInterface(named("com.amazonaws.services.lambda.runtime.RequestStreamHandler"))
        .and(not(nameStartsWith("com.amazonaws.services.lambda.runtime.api.client")))
        // In Java 8 and Java 11 runtimes,
        // AWS Lambda runtime is packaged under `lambdainternal` package.
        // But it is `com.amazonaws.services.lambda.runtime.api.client`
        // for new runtime likes Java 17 and Java 21.
        .and(not(nameStartsWith("lambdainternal")));
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) InputStream input,
        @Advice.Argument(2) Context context,
        @Advice.Local("otelInput") AwsLambdaRequest otelInput,
        @Advice.Local("otelContext") io.opentelemetry.context.Context otelContext,
        @Advice.Local("otelScope") Scope otelScope) {

      otelInput = AwsLambdaRequest.create(context, input, Collections.emptyMap());
      io.opentelemetry.context.Context parentContext = functionInstrumenter().extract(otelInput);

      if (!functionInstrumenter().shouldStart(parentContext, otelInput)) {
        return;
      }

      otelContext = functionInstrumenter().start(parentContext, otelInput);
      otelScope = otelContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelInput") AwsLambdaRequest input,
        @Advice.Local("otelContext") io.opentelemetry.context.Context functionContext,
        @Advice.Local("otelScope") Scope functionScope) {

      if (functionScope != null) {
        functionScope.close();
        functionInstrumenter().end(functionContext, input, null, throwable);
      }

      OpenTelemetrySdkAccess.forceFlush(flushTimeout().toNanos(), TimeUnit.NANOSECONDS);
    }
  }
}
