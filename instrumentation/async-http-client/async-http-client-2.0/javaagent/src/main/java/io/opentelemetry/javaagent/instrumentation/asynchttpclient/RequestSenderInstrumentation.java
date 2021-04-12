/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.Request;
import org.asynchttpclient.netty.NettyResponseFuture;

public class RequestSenderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.asynchttpclient.netty.request.NettyRequestSender");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        named("sendRequest")
            .and(takesArgument(0, named("org.asynchttpclient.Request")))
            .and(takesArgument(1, named("org.asynchttpclient.AsyncHandler")))
            .and(isPublic()),
        RequestSenderInstrumentation.class.getName() + "$AttachContextAdvice");

    transformers.put(
        named("writeRequest")
            .and(takesArgument(0, named("org.asynchttpclient.netty.NettyResponseFuture")))
            .and(takesArgument(1, named("io.netty.channel.Channel")))
            .and(isPublic()),
        RequestSenderInstrumentation.class.getName() + "$MountContextAdvice");

    return transformers;
  }

  public static class AttachContextAdvice {
    @Advice.OnMethodEnter
    public static void attachContext(@Advice.Argument(0) Request request) {
      InstrumentationContext.get(Request.class, Context.class)
          .put(request, Java8BytecodeBridge.currentContext());
    }
  }

  public static class MountContextAdvice {
    @Advice.OnMethodEnter
    public static Scope mountContext(@Advice.Argument(0) NettyResponseFuture<?> responseFuture) {
      Request request = responseFuture.getCurrentRequest();
      Context context = InstrumentationContext.get(Request.class, Context.class).get(request);
      return context == null ? null : context.makeCurrent();
    }

    @Advice.OnMethodExit
    public static void unmountContext(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
