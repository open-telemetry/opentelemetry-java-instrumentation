/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.connectInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyConnectRequest;
import java.net.SocketAddress;
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
        named("connect").and(takesArgument(1, named("java.net.SocketAddress"))),
        TransportConnectorInstrumentation.class.getName() + "$ConnectAdvice");
  }

  public static class ConnectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startConnect(
        @Advice.Argument(1) SocketAddress remoteAddress,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelRequest") NettyConnectRequest request,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = NettyConnectRequest.create(remoteAddress);
      if (!connectInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = connectInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endConnect(
        @Advice.Thrown Throwable throwable,
        @Advice.Return(readOnly = false) Mono<Channel> mono,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelRequest") NettyConnectRequest request,
        @Advice.Local("otelScope") Scope scope) {

      if (scope != null) {
        scope.close();
      }

      if (throwable != null) {
        connectInstrumenter().end(context, request, null, throwable);
      } else {
        mono = ConnectionWrapper.wrap(context, request, mono);
      }
    }
  }
}
