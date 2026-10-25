/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyContentContext.CallbackScope;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.HttpConversation;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

class JettyRequestBoundaryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.eclipse.jetty.client.RequestNotifier",
        "org.eclipse.jetty.client.HttpConnection",
        "org.eclipse.jetty.client.HttpRequest",
        "org.eclipse.jetty.client.HttpConversation");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("notify")
            .and(isPublic())
            .and(takesArgument(0, named("org.eclipse.jetty.client.api.Request")))
            .or(
                named("normalizeRequest")
                    .and(takesArgument(0, named("org.eclipse.jetty.client.api.Request")))),
        getClass().getName() + "$RequestAdvice");
    transformer.applyAdviceToMethod(
        named("abort")
            .and(takesArguments(Throwable.class))
            .and(isDeclaredBy(named("org.eclipse.jetty.client.HttpRequest"))),
        getClass().getName() + "$AbortAdvice");
    transformer.applyAdviceToMethod(
        named("updateResponseListeners")
            .and(takesArgument(0, named("org.eclipse.jetty.client.api.Response$ResponseListener"))),
        getClass().getName() + "$ProtocolAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProtocolAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HttpConversation conversation,
        @Advice.Argument(0) @Nullable Response.ResponseListener listener) {
      JettyRequestContext.updateProtocolResponse(conversation, listener);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CallbackScope onEnter(@Advice.Argument(0) Request request) {
      JettyContentContext caller = JettyRequestContext.getCaller(request);
      return caller == null ? null : caller.open();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable CallbackScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AbortAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CallbackScope onEnter(@Advice.This HttpRequest request) {
      JettyContentContext caller = JettyRequestContext.getCaller(request);
      return caller == null ? null : caller.open();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable CallbackScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
