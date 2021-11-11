/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyClientSingletons.connectionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.ChannelFuture;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.ConnectionCompleteListener;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class BootstrapInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.netty.bootstrap.Bootstrap");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doConnect")
            .and(takesArgument(0, SocketAddress.class))
            .and(returns(named("io.netty.channel.ChannelFuture"))),
        BootstrapInstrumentation.class.getName() + "$ConnectAdvice");
  }

  public static class ConnectAdvice {
    @Advice.OnMethodEnter
    public static void startConnect(
        @Advice.Argument(0) SocketAddress remoteAddress,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelRequest") NettyConnectionRequest request,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = NettyConnectionRequest.connect(remoteAddress);

      if (!connectionInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = connectionInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endConnect(
        @Advice.Thrown Throwable throwable,
        @Advice.Return ChannelFuture channelFuture,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelRequest") NettyConnectionRequest request,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        connectionInstrumenter().end(context, request, null, throwable);
      } else {
        channelFuture.addListener(
            new ConnectionCompleteListener(connectionInstrumenter(), context, request));
      }
    }
  }
}
