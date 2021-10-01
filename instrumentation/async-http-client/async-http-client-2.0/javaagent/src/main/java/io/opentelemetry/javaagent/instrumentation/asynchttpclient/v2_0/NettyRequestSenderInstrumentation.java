/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.Request;
import org.asynchttpclient.netty.NettyResponseFuture;

public class NettyRequestSenderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.asynchttpclient.netty.request.NettyRequestSender");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("sendRequest")
            .and(takesArgument(0, named("org.asynchttpclient.Request")))
            .and(takesArgument(1, named("org.asynchttpclient.AsyncHandler")))
            .and(isPublic()),
        NettyRequestSenderInstrumentation.class.getName() + "$AttachContextAdvice");

    transformer.applyAdviceToMethod(
        named("writeRequest")
            .and(takesArgument(0, named("org.asynchttpclient.netty.NettyResponseFuture")))
            .and(takesArgument(1, named("io.netty.channel.Channel")))
            .and(isPublic()),
        NettyRequestSenderInstrumentation.class.getName() + "$MountContextAdvice");

    transformer.applyAdviceToMethod(
        named("newNettyRequestAndResponseFuture")
            .and(takesArgument(0, named("org.asynchttpclient.Request")))
            .and(takesArgument(1, named("org.asynchttpclient.AsyncHandler")))
            .and(returns(named("org.asynchttpclient.netty.NettyResponseFuture"))),
        NettyRequestSenderInstrumentation.class.getName() + "$RememberNettyRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class AttachContextAdvice {

    @Advice.OnMethodEnter
    public static void attachContext(@Advice.Argument(0) Request request) {
      VirtualField.find(Request.class, Context.class)
          .set(request, Java8BytecodeBridge.currentContext());
    }
  }

  @SuppressWarnings("unused")
  public static class MountContextAdvice {

    @Advice.OnMethodEnter
    public static Scope mountContext(@Advice.Argument(0) NettyResponseFuture<?> responseFuture) {
      Request request = responseFuture.getCurrentRequest();
      Context context = VirtualField.find(Request.class, Context.class).get(request);
      return context == null ? null : context.makeCurrent();
    }

    @Advice.OnMethodExit
    public static void unmountContext(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RememberNettyRequestAdvice {

    @Advice.OnMethodExit
    public static void rememberNettyRequest(@Advice.Return NettyResponseFuture responseFuture) {
      RequestContext requestContext =
          VirtualField.find(AsyncHandler.class, RequestContext.class)
              .get(responseFuture.getAsyncHandler());
      if (requestContext != null) {
        requestContext.setNettyRequest(responseFuture.getNettyRequest());
      }
    }
  }
}
