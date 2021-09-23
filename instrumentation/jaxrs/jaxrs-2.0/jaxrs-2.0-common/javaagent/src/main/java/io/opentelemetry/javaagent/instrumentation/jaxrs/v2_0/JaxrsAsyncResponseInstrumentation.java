/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
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

      ContextStore<AsyncResponse, AsyncResponseData> contextStore =
          InstrumentationContext.get(AsyncResponse.class, AsyncResponseData.class);

      AsyncResponseData data = contextStore.get(asyncResponse);
      if (data != null) {
        contextStore.put(asyncResponse, null);
        instrumenter().end(data.getContext(), data.getHandlerData(), null, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncResponseThrowableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This AsyncResponse asyncResponse, @Advice.Argument(0) Throwable throwable) {

      ContextStore<AsyncResponse, AsyncResponseData> contextStore =
          InstrumentationContext.get(AsyncResponse.class, AsyncResponseData.class);

      AsyncResponseData data = contextStore.get(asyncResponse);
      if (data != null) {
        contextStore.put(asyncResponse, null);
        instrumenter().end(data.getContext(), data.getHandlerData(), null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncResponseCancelAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopSpan(@Advice.This AsyncResponse asyncResponse) {

      ContextStore<AsyncResponse, AsyncResponseData> contextStore =
          InstrumentationContext.get(AsyncResponse.class, AsyncResponseData.class);

      AsyncResponseData data = contextStore.get(asyncResponse);
      if (data != null) {
        contextStore.put(asyncResponse, null);
        Context context = data.getContext();
        if (JaxrsConfig.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
          Java8BytecodeBridge.spanFromContext(context).setAttribute("jaxrs.canceled", true);
        }
        instrumenter().end(context, data.getHandlerData(), null, null);
      }
    }
  }
}
