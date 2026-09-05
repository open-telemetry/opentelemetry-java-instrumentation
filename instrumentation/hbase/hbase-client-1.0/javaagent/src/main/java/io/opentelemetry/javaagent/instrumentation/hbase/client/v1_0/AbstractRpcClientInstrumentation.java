/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientUtil.createRequest;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v1_0.HbaseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseServerTarget;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ipc.AbstractRpcClient;
import org.apache.hadoop.hbase.security.User;

class AbstractRpcClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.ipc.AbstractRpcClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("org.apache.hadoop.conf.Configuration"))),
        getClass().getName() + "$ConstructorAdvice");

    // 1.0.0-1.3.x: callBlockingMethod(md, pcrc, param, returnType, ticket, addr)
    transformer.applyAdviceToMethod(
        named("callBlockingMethod")
            .and(takesArguments(6))
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "com.google.protobuf.Descriptors$MethodDescriptor",
                        "org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User")))
            .and(takesArgument(5, InetSocketAddress.class)),
        getClass().getName() + "$CallBlockingMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This AbstractRpcClient client, @Advice.Argument(0) Configuration configuration) {
      HbaseServerTarget.store(client, configuration);
    }
  }

  public static class AdviceScope {
    private final HbaseRequest request;
    private final Context context;
    private final Scope scope;

    private AdviceScope(HbaseRequest request, Context context) {
      this.request = request;
      this.context = context;
      this.scope = context.makeCurrent();
    }

    @Nullable
    public static AdviceScope start(
        Object md,
        Object param,
        User ticket,
        InetSocketAddress addr,
        @Nullable String serverTarget) {
      HbaseRequest request = createRequest(md, param, ticket, addr, serverTarget);
      Context parentContext = Context.current();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(request, context);
    }

    public void end(@Nullable Throwable throwable) {
      scope.close();
      instrumenter().end(context, request, null, unwrapServiceException(throwable));
    }

    @Nullable
    private static Throwable unwrapServiceException(@Nullable Throwable throwable) {
      if (throwable == null || throwable.getCause() == null) {
        return throwable;
      }

      // callBlockingMethod wraps RPC failures in the unshaded or shaded protobuf exception.
      String className = throwable.getClass().getName();
      if (className.equals("com.google.protobuf.ServiceException")
          || className.equals(
              "org.apache.hadoop.hbase.shaded.com.google.protobuf.ServiceException")) {
        return throwable.getCause();
      }
      return throwable;
    }
  }

  @SuppressWarnings("unused")
  public static class CallBlockingMethodAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This AbstractRpcClient client,
        @Advice.Argument(0) Object md,
        @Advice.Argument(2) Object param,
        @Advice.Argument(4) User ticket,
        @Advice.Argument(5) InetSocketAddress addr) {
      return AdviceScope.start(md, param, ticket, addr, HbaseServerTarget.get(client));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
