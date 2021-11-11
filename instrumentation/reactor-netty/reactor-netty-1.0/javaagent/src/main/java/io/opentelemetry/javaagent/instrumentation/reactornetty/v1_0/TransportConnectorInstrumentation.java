/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.connectionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.resolver.AddressResolverGroup;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.InstrumentedAddressResolverGroup;
import java.net.SocketAddress;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;

public class TransportConnectorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.netty.transport.TransportConnector");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doResolveAndConnect")
            .and(takesArgument(3, named("io.netty.resolver.AddressResolverGroup")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        TransportConnectorInstrumentation.class.getName() + "$ResolveAndConnectAdvice");

    // handles [1.0.0, 1.0.6)
    transformer.applyAdviceToMethod(
        named("doConnect")
            .and(takesArgument(0, SocketAddress.class))
            .and(takesArgument(2, named("io.netty.channel.ChannelPromise"))),
        TransportConnectorInstrumentation.class.getName() + "$ConnectAdvice");
    // handles [1.0.6, )
    transformer.applyAdviceToMethod(
        named("doConnect")
            .and(takesArgument(0, List.class))
            .and(takesArgument(2, named("io.netty.channel.ChannelPromise")))
            .and(takesArgument(3, int.class)),
        TransportConnectorInstrumentation.class.getName() + "$ConnectNewAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResolveAndConnectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 3, readOnly = false) AddressResolverGroup<?> resolver) {
      resolver = InstrumentedAddressResolverGroup.wrap(connectionInstrumenter(), resolver);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) Mono<Channel> mono) {
      // end the CONNECT span that was started in doConnect() instrumentation
      mono = ConnectionWrapper.wrap(mono);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
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
        @Advice.Argument(2) ChannelPromise channelPromise,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelRequest") NettyConnectionRequest request,
        @Advice.Local("otelScope") Scope scope) {

      if (scope != null) {
        scope.close();
      }

      if (throwable != null) {
        connectionInstrumenter().end(context, request, null, throwable);
      } else {
        // the span is finished in the mono decorated by the ConnectionWrapper
        VirtualField.find(ChannelPromise.class, ConnectionRequestAndContext.class)
            .set(channelPromise, ConnectionRequestAndContext.create(request, context));
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectNewAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) List<SocketAddress> remoteAddresses,
        @Advice.Argument(3) int index,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelRequest") NettyConnectionRequest request,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = NettyConnectionRequest.connect(remoteAddresses.get(index));
      if (!connectionInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = connectionInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endConnect(
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(2) ChannelPromise channelPromise,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelRequest") NettyConnectionRequest request,
        @Advice.Local("otelScope") Scope scope) {

      if (scope != null) {
        scope.close();
      }

      if (throwable != null) {
        connectionInstrumenter().end(context, request, null, throwable);
      } else {
        // the span is finished in the mono decorated by the ConnectionWrapper
        VirtualField.find(ChannelPromise.class, ConnectionRequestAndContext.class)
            .set(channelPromise, ConnectionRequestAndContext.create(request, context));
      }
    }
  }
}
