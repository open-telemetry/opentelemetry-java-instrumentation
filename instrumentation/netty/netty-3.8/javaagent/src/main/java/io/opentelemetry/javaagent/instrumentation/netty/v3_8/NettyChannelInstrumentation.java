/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.connectionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.ConnectionListener;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

public class NettyChannelInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.jboss.netty.channel.Channel");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.jboss.netty.channel.Channel"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("connect"))
            .and(takesArgument(0, SocketAddress.class))
            .and(returns(named("org.jboss.netty.channel.ChannelFuture"))),
        NettyChannelInstrumentation.class.getName() + "$ChannelConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ChannelConnectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Channel channel,
        @Advice.Argument(0) SocketAddress remoteAddress,
        @Advice.Local("otelParentContext") Context parentContext,
        @Advice.Local("otelRequest") NettyConnectionRequest request) {

      parentContext = Java8BytecodeBridge.currentContext();
      Span span = Java8BytecodeBridge.spanFromContext(parentContext);
      if (!span.getSpanContext().isValid()) {
        return;
      }

      VirtualField<Channel, NettyConnectionContext> virtualField =
          VirtualField.find(Channel.class, NettyConnectionContext.class);
      if (virtualField.get(channel) != null) {
        return;
      }
      virtualField.set(channel, new NettyConnectionContext(parentContext));

      request = NettyConnectionRequest.connect(remoteAddress);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Return ChannelFuture channelFuture,
        @Advice.Thrown Throwable error,
        @Advice.Local("otelParentContext") Context parentContext,
        @Advice.Local("otelRequest") NettyConnectionRequest request) {

      if (request == null) {
        return;
      }

      if (error != null) {
        if (connectionInstrumenter().shouldStart(parentContext, request)) {
          Context context = connectionInstrumenter().start(parentContext, request);
          connectionInstrumenter().end(context, request, null, error);
        }
      } else {
        channelFuture.addListener(new ConnectionListener(parentContext, request));
      }
    }
  }
}
