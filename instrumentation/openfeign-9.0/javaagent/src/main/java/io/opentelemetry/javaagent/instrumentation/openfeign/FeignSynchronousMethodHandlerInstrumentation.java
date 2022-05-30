/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.openfeign.OpenFeignInstrumentationSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import feign.MethodMetadata;
import feign.RequestTemplate;
import feign.Response;
import feign.Target;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class FeignSynchronousMethodHandlerInstrumentation implements TypeInstrumentation {

  public static final String FEIGN_SYNCHRONOUS_METHOD_HANDLER = "feign.SynchronousMethodHandler";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(FEIGN_SYNCHRONOUS_METHOD_HANDLER);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(FEIGN_SYNCHRONOUS_METHOD_HANDLER);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("executeAndDecode")),
        FeignSynchronousMethodHandlerInstrumentation.class.getName() + "$ExecuteAndDecodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAndDecodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) RequestTemplate requestTemplate,
        @Advice.FieldValue("target") Target<?> target,
        @Advice.FieldValue("metadata") MethodMetadata metadata,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("invokeParam") ExecuteAndDecodeRequest request) {

      request = new ExecuteAndDecodeRequest(target, metadata, requestTemplate);
      context = instrumenter().start(Java8BytecodeBridge.currentContext(), request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.FieldValue("target") Target<?> target,
        @Advice.FieldValue("metadata") MethodMetadata metadata,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("invokeParam") ExecuteAndDecodeRequest request) {
      if (scope == null) {
        return;
      }
      Response response = OpenFeignResponseHolder.get(context);
      instrumenter().end(context, request, response, throwable);
      scope.close();
    }
  }
}
