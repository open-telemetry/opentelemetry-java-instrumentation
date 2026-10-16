/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network;

import static io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.CouchbaseNetworkVirtualFields.COUCHBASE_REQUEST_INFO;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.message.CouchbaseRequest;
import com.couchbase.client.deps.io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class Couchbase20NetworkInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.endpoint.AbstractGenericHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("encode")
            .and(takesArguments(3))
            .and(
                takesArgument(
                    0, named("com.couchbase.client.deps.io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, named("com.couchbase.client.core.message.CouchbaseRequest"))),
        getClass().getName() + "$CouchbaseNetworkAdvice");
  }

  @SuppressWarnings("unused")
  public static class CouchbaseNetworkAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void addNetworkTagsToSpan(
        @Advice.Argument(0) ChannelHandlerContext channelHandlerContext,
        @Advice.Argument(1) CouchbaseRequest request) {

      CouchbaseRequestInfo requestInfo = COUCHBASE_REQUEST_INFO.get(request);
      if (requestInfo != null) {
        requestInfo.setNode(channelHandlerContext.channel().remoteAddress());
      }
    }
  }
}
