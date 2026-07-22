/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.getTableName;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v1_0.HbaseSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v1_0.HbaseSingletons.methodDescriptorName;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos;
import org.apache.hadoop.hbase.security.User;

class AbstractRpcClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.hadoop.hbase.ipc.AbstractRpcClient"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.google.protobuf.Descriptors")
        .or(hasClassesNamed("org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("call")
            .and(takesArgument(1, named("com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(5, named("org.apache.hadoop.hbase.security.User")))
            .and(takesArgument(6, named("java.net.InetSocketAddress"))),
        getClass().getName() + "$RpcCall10Advice");

    transformer.applyAdviceToMethod(
        named("call")
            .and(
                takesArgument(
                    1,
                    namedOneOf(
                        "com.google.protobuf.Descriptors$MethodDescriptor",
                        "org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User")))
            .and(takesArgument(5, named("java.net.InetSocketAddress"))),
        getClass().getName() + "$RpcCallLaterAdvice");
  }

  @SuppressWarnings("unused")
  public static class RpcCall10Advice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static RequestAndContext onEnter(
        @Advice.Argument(1) Object md,
        @Advice.Argument(2) Object param,
        @Advice.Argument(5) User ticket,
        @Advice.Argument(6) InetSocketAddress addr) {
      String operation = methodDescriptorName(md);
      Long batchSize = null;
      if (emitStableDatabaseSemconv() && param instanceof ClientProtos.MultiRequest) {
        HbaseBatchMetadata batchMetadata =
            HbaseBatchMetadata.create((ClientProtos.MultiRequest) param);
        operation = batchMetadata.getOperation();
        batchSize = batchMetadata.getOperationBatchSize();
      }

      HbaseRequest request =
          HbaseRequest.create(
              operation,
              getTableName(),
              ticket.getName(),
              addr.getHostString(),
              addr.getPort(),
              batchSize);
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      Scope scope = context.makeCurrent();
      return RequestAndContext.create(request, scope, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable RequestAndContext requestAndContext) {
      if (requestAndContext == null) {
        return;
      }

      requestAndContext.getScope().close();
      instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RpcCallLaterAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static RequestAndContext onEnter(
        @Advice.Argument(1) Object md,
        @Advice.Argument(2) Object param,
        @Advice.Argument(4) User ticket,
        @Advice.Argument(5) InetSocketAddress addr) {
      String operation = methodDescriptorName(md);
      Long batchSize = null;
      if (emitStableDatabaseSemconv() && param instanceof ClientProtos.MultiRequest) {
        HbaseBatchMetadata batchMetadata =
            HbaseBatchMetadata.create((ClientProtos.MultiRequest) param);
        operation = batchMetadata.getOperation();
        batchSize = batchMetadata.getOperationBatchSize();
      }

      HbaseRequest request =
          HbaseRequest.create(
              operation,
              getTableName(),
              ticket.getName(),
              addr.getHostString(),
              addr.getPort(),
              batchSize);
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      Scope scope = context.makeCurrent();
      return RequestAndContext.create(request, scope, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable RequestAndContext requestAndContext) {
      if (requestAndContext == null) {
        return;
      }

      requestAndContext.getScope().close();
      instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, throwable);
    }
  }
}
