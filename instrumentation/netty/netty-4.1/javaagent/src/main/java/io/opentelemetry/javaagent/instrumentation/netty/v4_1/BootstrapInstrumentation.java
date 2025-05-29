/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons.connectionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelPromise;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.NettyScope;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
        isConstructor().and(takesArguments(0)),
        BootstrapInstrumentation.class.getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("resolver")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.netty.resolver.AddressResolverGroup"))),
        BootstrapInstrumentation.class.getName() + "$SetResolverAdvice");
    transformer.applyAdviceToMethod(
        named("doConnect")
            .and(takesArguments(3))
            .and(takesArgument(0, SocketAddress.class))
            .and(takesArgument(2, named("io.netty.channel.ChannelPromise"))),
        BootstrapInstrumentation.class.getName() + "$ConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Bootstrap bootstrap) {
      // this is already the default value, but we're calling the resolver() method to invoke its
      // instrumentation
      bootstrap.resolver(DefaultAddressResolverGroup.INSTANCE);
    }
  }

  @SuppressWarnings("unused")
  public static class SetResolverAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    public static AddressResolverGroup<?> onEnter(
        @Advice.Argument(value = 0) AddressResolverGroup<?> resolver) {
      return InstrumentedAddressResolverGroup.wrap(connectionInstrumenter(), resolver);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {
    @Advice.OnMethodEnter
    public static NettyScope startConnect(@Advice.Argument(0) SocketAddress remoteAddress) {

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
        @Advice.Argument(2) ChannelPromise channelPromise,
        @Advice.Enter NettyScope enterScope) {

      NettyScope.end(enterScope, connectionInstrumenter(), channelPromise, throwable);
    }
  }
}
