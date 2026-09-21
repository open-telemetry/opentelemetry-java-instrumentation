/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.deps.io.netty.channel.ChannelHandlerContext;
import com.couchbase.client.core.msg.Request;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CouchbaseMessageHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.couchbase.client.core.io.netty.kv.KeyValueMessageHandler",
        "com.couchbase.client.core.io.netty.NonChunkedHttpMessageHandler",
        "com.couchbase.client.core.io.netty.chunk.ChunkedMessageHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write")
            .and(takesArguments(3))
            .and(
                takesArgument(
                    0,
                    named(
                        "com.couchbase.client.core.deps.io.netty.channel.ChannelHandlerContext"))),
        getClass().getName() + "$WriteAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static CouchbaseRequestPeers.RequestPeerScope onEnter(
        @Advice.Argument(0) ChannelHandlerContext context, @Advice.Argument(1) Object message) {
      if (!(message instanceof Request)) {
        return null;
      }
      RequestSpan parent = ((Request<?>) message).requestSpan();
      return CouchbaseRequestPeers.open(parent, context.channel().remoteAddress());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable CouchbaseRequestPeers.RequestPeerScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
