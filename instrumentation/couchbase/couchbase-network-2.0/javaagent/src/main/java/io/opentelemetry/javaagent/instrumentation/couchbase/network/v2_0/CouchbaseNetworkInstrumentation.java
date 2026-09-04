/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.network.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.couchbase.network.v2_0.VirtualFieldHelper.COUCHBASE_REQUEST_INFO;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.message.CouchbaseRequest;
import com.couchbase.client.deps.io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CouchbaseNetworkInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.endpoint.AbstractGenericHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // encode(ChannelHandlerContext ctx, REQUEST msg, List<Object> out)
    transformer.applyAdviceToMethod(
        named("encode")
            .and(takesArguments(3))
            .and(
                takesArgument(
                    0, named("com.couchbase.client.deps.io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(2, List.class)),
        getClass().getName() + "$CouchbaseNetworkAdvice");
  }

  @SuppressWarnings("unused")
  public static class CouchbaseNetworkAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void addNetworkTagsToSpan(
        @Advice.Argument(0) ChannelHandlerContext channelHandlerContext,
        @Advice.Argument(1) CouchbaseRequest request) {

      // The core-io versions before 1.6.0 have no reliable, version-stable way to read the node
      // string the driver considers itself connected to, so unlike couchbase-2.6 this only records
      // the actual peer connection. The old semantic conventions describe these spans with that
      // node string, so they are left as they are.
      if (!emitStableDatabaseSemconv()) {
        return;
      }

      CouchbaseRequestInfo requestInfo = COUCHBASE_REQUEST_INFO.get(request);
      if (requestInfo != null) {
        requestInfo.setNode(channelHandlerContext.channel().remoteAddress(), null);
      }
    }
  }
}
