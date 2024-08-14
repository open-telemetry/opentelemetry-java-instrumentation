/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyClientSingletons.connectionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.ChannelPromise;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.NettyScope;
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
        named("doConnect0")
            .and(takesArgument(2, SocketAddress.class))
            .and(takesArgument(4, named("io.netty.channel.ChannelPromise"))),
        BootstrapInstrumentation.class.getName() + "$ConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {
    @Advice.OnMethodEnter
    public static NettyScope startConnect(@Advice.Argument(2) SocketAddress remoteAddress) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      NettyConnectionRequest request = NettyConnectionRequest.connect(remoteAddress);

      if (!connectionInstrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      return NettyScope.startNew(connectionInstrumenter(), parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endConnect(
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(4) ChannelPromise channelPromise,
        @Advice.Enter NettyScope enterScope) {

      NettyScope.end(enterScope, connectionInstrumenter(), channelPromise, throwable);
    }
  }
}
