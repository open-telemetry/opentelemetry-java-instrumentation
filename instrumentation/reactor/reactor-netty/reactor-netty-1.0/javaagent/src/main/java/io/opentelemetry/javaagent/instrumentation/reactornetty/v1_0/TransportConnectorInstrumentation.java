/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.CONNECTION_REQUEST_AND_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.connectionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.resolver.AddressResolverGroup;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.InstrumentedAddressResolverGroup;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
            .and(
                takesArgument(
                    2,
                    namedOneOf(
                        "io.netty.channel.ChannelPromise",
                        // since 1.0.34
                        "reactor.netty.transport.TransportConnector$MonoChannelPromise")))
            .and(takesArgument(3, int.class)),
        TransportConnectorInstrumentation.class.getName() + "$ConnectNewAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResolveAndConnectAdvice {

    @AssignReturned.ToArguments(@ToArgument(3))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AddressResolverGroup<?> onEnter(
        @Advice.Argument(3) AddressResolverGroup<?> resolver) {
      return InstrumentedAddressResolverGroup.wrap(connectionInstrumenter(), resolver);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Mono<Channel> onExit(@Advice.Return Mono<Channel> mono) {

      // end the CONNECT span that was started in doConnect() instrumentation
      return ConnectionWrapper.wrap(mono);
    }
  }

  public static class AdviceScope {
    private final NettyConnectionRequest request;
    private final Context context;
    private final Scope scope;

    private AdviceScope(NettyConnectionRequest request, Context context, Scope scope) {
      this.request = request;
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(SocketAddress remoteAddress, ChannelPromise channelPromise) {

      Context parentContext = Context.current();
      NettyConnectionRequest request = NettyConnectionRequest.connect(remoteAddress);
      if (!connectionInstrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = connectionInstrumenter().start(parentContext, request);
      // the span is finished in the mono decorated by the ConnectionWrapper
      CONNECTION_REQUEST_AND_CONTEXT.set(
          channelPromise, ConnectionRequestAndContext.create(request, context));
      return new AdviceScope(request, context, context.makeCurrent());
    }

    public void end(@Nullable Throwable throwable) {
      scope.close();
      if (throwable != null) {
        connectionInstrumenter().end(context, request, null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(0) SocketAddress remoteAddress,
        @Advice.Argument(2) ChannelPromise channelPromise) {
      return AdviceScope.start(remoteAddress, channelPromise);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endConnect(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectNewAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(0) List<SocketAddress> remoteAddresses,
        @Advice.Argument(2) ChannelPromise channelPromise,
        @Advice.Argument(3) int index) {
      SocketAddress remoteAddress = remoteAddresses.get(index);
      return AdviceScope.start(remoteAddress, channelPromise);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endConnect(
        @Advice.Thrown Throwable throwable, @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
