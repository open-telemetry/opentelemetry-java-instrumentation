/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.ning.http.client.AsyncCompletionHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperClass(named("com.ning.http.client.AsyncCompletionHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onCompleted")
            .and(takesArgument(0, named("com.ning.http.client.Response")))
            .and(isPublic()),
        this.getClass().getName() + "$OnCompletedAdvice");
    transformer.applyAdviceToMethod(
        named("onThrowable").and(takesArgument(0, Throwable.class)).and(isPublic()),
        this.getClass().getName() + "$OnThrowableAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnCompletedAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(
        @Advice.This AsyncCompletionHandler<?> handler, @Advice.Argument(0) Response response) {

      VirtualField<AsyncHandler<?>, AsyncHandlerData> asyncHandlerDataField =
          VirtualField.find(AsyncHandler.class, AsyncHandlerData.class);
      AsyncHandlerData data = asyncHandlerDataField.get(handler);
      if (data == null) {
        return null;
      }
      asyncHandlerDataField.set(handler, null);

      Instrumenter<Request, Response> instrumenter = data.getInstrumenter();
      if (instrumenter != null) {
        instrumenter.end(data.getContext(), data.getRequest(), response, null);
      }
      return data.getParentContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (null != scope) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnThrowableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(
        @Advice.This AsyncCompletionHandler<?> handler, @Advice.Argument(0) Throwable throwable) {

      VirtualField<AsyncHandler<?>, AsyncHandlerData> asyncHandlerDataField =
          VirtualField.find(AsyncHandler.class, AsyncHandlerData.class);
      AsyncHandlerData data = asyncHandlerDataField.get(handler);
      if (data == null) {
        return null;
      }
      asyncHandlerDataField.set(handler, null);

      // Get instrumenter directly from data - much cleaner!
      Instrumenter<Request, Response> instrumenter = data.getInstrumenter();
      if (instrumenter != null) {
        instrumenter.end(data.getContext(), data.getRequest(), null, throwable);
      }
      return data.getParentContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (null != scope) {
        scope.close();
      }
    }
  }
}
