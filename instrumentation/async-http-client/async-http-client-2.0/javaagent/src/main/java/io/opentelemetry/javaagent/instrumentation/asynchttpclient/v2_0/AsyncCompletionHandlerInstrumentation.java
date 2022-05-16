/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0.AsyncHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.Response;

public class AsyncCompletionHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.asynchttpclient.AsyncCompletionHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperClass(named("org.asynchttpclient.AsyncCompletionHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onCompleted")
            .and(takesArgument(0, named("org.asynchttpclient.Response")))
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

      VirtualField<AsyncHandler<?>, RequestContext> virtualField =
          VirtualField.find(AsyncHandler.class, RequestContext.class);
      RequestContext requestContext = virtualField.get(handler);
      if (requestContext == null) {
        return null;
      }
      virtualField.set(handler, null);
      instrumenter().end(requestContext.getContext(), requestContext, response, null);
      return requestContext.getParentContext().makeCurrent();
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

      VirtualField<AsyncHandler<?>, RequestContext> virtualField =
          VirtualField.find(AsyncHandler.class, RequestContext.class);
      RequestContext requestContext = virtualField.get(handler);
      if (requestContext == null) {
        return null;
      }
      virtualField.set(handler, null);
      instrumenter().end(requestContext.getContext(), requestContext, null, throwable);
      return requestContext.getParentContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (null != scope) {
        scope.close();
      }
    }
  }
}
