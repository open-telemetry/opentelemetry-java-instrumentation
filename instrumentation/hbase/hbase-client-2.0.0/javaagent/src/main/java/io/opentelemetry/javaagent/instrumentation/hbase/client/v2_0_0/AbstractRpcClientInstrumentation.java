/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0.HbaseSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.hbase.common.HbaseInstrumenterFactory.RC_THREAD_LOCAL;
import static io.opentelemetry.javaagent.instrumentation.hbase.common.HbaseInstrumenterFactory.TABLE_THREAD_LOCAL;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hbase.common.CallMethodHelper;
import io.opentelemetry.javaagent.instrumentation.hbase.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.common.RequestAndContext;
import java.net.InetSocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.net.Address;
import org.apache.hadoop.hbase.security.User;
import org.apache.hbase.thirdparty.com.google.protobuf.Descriptors;

public final class AbstractRpcClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.ipc.AbstractRpcClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("callMethod")
            .and(
                takesArgument(
                    0,
                    named(
                        "org.apache.hbase.thirdparty.com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User")))
            .and(takesArgument(5, named("java.net.InetSocketAddress"))),
        AbstractRpcClientInstrumentation.class.getName() + "$CallMethodAdvice");

    // 2.4.18 version
    transformer.applyAdviceToMethod(
        named("callMethod")
            .and(
                takesArgument(
                    0,
                    named(
                        "org.apache.hbase.thirdparty.com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User")))
            .and(takesArgument(5, named("org.apache.hadoop.hbase.net.Address"))),
        AbstractRpcClientInstrumentation.class.getName() + "$CallMethodLastAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallMethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Descriptors.MethodDescriptor md,
        @Advice.Argument(4) User ticket,
        @Advice.Argument(5) InetSocketAddress addr,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelHbaseRequest") HbaseRequest request) {
      request =
          CallMethodHelper.buildRequest(
              md.getName(), TABLE_THREAD_LOCAL.get(), ticket.getName(), addr);
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
      RequestAndContext requestAndContext = RequestAndContext.create(request, context);
      RC_THREAD_LOCAL.set(requestAndContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelHbaseRequest") HbaseRequest request) {
      RC_THREAD_LOCAL.remove();
      CallMethodHelper.handleOnExit(throwable, scope, context, request, instrumenter(), false);
    }
  }

  @SuppressWarnings("unused")
  public static class CallMethodLastAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Descriptors.MethodDescriptor md,
        @Advice.Argument(4) User ticket,
        @Advice.Argument(5) Address addr,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelHbaseRequest") HbaseRequest request) {
      InetSocketAddress isa =
          InetSocketAddress.createUnresolved(addr.getHostname(), addr.getPort());
      request =
          CallMethodHelper.buildRequest(
              md.getName(), TABLE_THREAD_LOCAL.get(), ticket.getName(), isa);
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();

      RequestAndContext requestAndContext = RequestAndContext.create(request, context);
      RC_THREAD_LOCAL.set(requestAndContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelHbaseRequest") HbaseRequest request) {
      RC_THREAD_LOCAL.remove();
      CallMethodHelper.handleOnExit(throwable, scope, context, request, instrumenter(), false);
    }
  }
}
