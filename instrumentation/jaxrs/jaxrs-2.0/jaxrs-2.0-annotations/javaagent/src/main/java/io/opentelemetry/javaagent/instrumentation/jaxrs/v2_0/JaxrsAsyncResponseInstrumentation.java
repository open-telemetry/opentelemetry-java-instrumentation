/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsAnnotationsSingletons.ASYNC_RESPONSE_DATA;
import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsAnnotationsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jaxrs.AsyncResponseData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConfig;
import javax.ws.rs.container.AsyncResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JaxrsAsyncResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.ws.rs.container.AsyncResponse");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.ws.rs.container.AsyncResponse"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("resume").and(takesArgument(0, Object.class)).and(isPublic()),
        JaxrsAsyncResponseInstrumentation.class.getName() + "$AsyncResponseAdvice");
    transformer.applyAdviceToMethod(
        named("resume").and(takesArgument(0, Throwable.class)).and(isPublic()),
        JaxrsAsyncResponseInstrumentation.class.getName() + "$AsyncResponseThrowableAdvice");
    transformer.applyAdviceToMethod(
        named("cancel"),
        JaxrsAsyncResponseInstrumentation.class.getName() + "$AsyncResponseCancelAdvice");
  }

  @SuppressWarnings("unused")
  public static class AsyncResponseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopSpan(@Advice.This AsyncResponse asyncResponse) {

      AsyncResponseData data = ASYNC_RESPONSE_DATA.get(asyncResponse);
      if (data != null) {
        ASYNC_RESPONSE_DATA.set(asyncResponse, null);
        instrumenter().end(data.getContext(), data.getHandlerData(), null, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncResponseThrowableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This AsyncResponse asyncResponse, @Advice.Argument(0) Throwable throwable) {

      AsyncResponseData data = ASYNC_RESPONSE_DATA.get(asyncResponse);
      if (data != null) {
        ASYNC_RESPONSE_DATA.set(asyncResponse, null);
        instrumenter().end(data.getContext(), data.getHandlerData(), null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncResponseCancelAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void stopSpan(@Advice.This AsyncResponse asyncResponse) {

      AsyncResponseData data = ASYNC_RESPONSE_DATA.get(asyncResponse);
      if (data != null) {
        ASYNC_RESPONSE_DATA.set(asyncResponse, null);
        Context context = data.getContext();
        if (JaxrsConfig.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
          Java8BytecodeBridge.spanFromContext(context).setAttribute("jaxrs.canceled", true);
        }
        instrumenter().end(context, data.getHandlerData(), null, null);
      }
    }
  }
}
