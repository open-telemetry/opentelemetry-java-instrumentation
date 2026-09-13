/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.ipc.OpenTelemetryCallUtil;
import org.apache.hbase.thirdparty.io.netty.channel.ChannelHandlerContext;

class NettyRpcDuplexHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.ipc.NettyRpcDuplexHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write")
            .and(
                takesArgument(
                    0, named("org.apache.hbase.thirdparty.io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, Object.class))
            .and(
                takesArgument(
                    2, named("org.apache.hbase.thirdparty.io.netty.channel.ChannelPromise"))),
        getClass().getName() + "$WriteAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ChannelHandlerContext context, @Advice.Argument(1) Object message) {
      if (OpenTelemetryCallUtil.isCall(message)) {
        OpenTelemetryCallUtil.setNetworkPeer(message, context.channel().remoteAddress());
      }
    }
  }
}
