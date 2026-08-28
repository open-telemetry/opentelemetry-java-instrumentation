/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.protocol.internal.Frame;
import io.opentelemetry.instrumentation.cassandra.v4_4.internal.CassandraNetworkPeer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class DefaultExecutionInfoInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.datastax.oss.driver.internal.core.cql.DefaultExecutionInfo");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(11))
            .and(takesArgument(6, named("com.datastax.oss.protocol.internal.Frame"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ExecutionInfo executionInfo, @Advice.Argument(6) Frame frame) {
      if (frame == null) {
        return;
      }
      InetSocketAddress peer = VirtualFieldHelper.FRAME_PEER.get(frame);
      if (peer != null) {
        CassandraNetworkPeer.set(executionInfo, peer);
      }
    }
  }
}
