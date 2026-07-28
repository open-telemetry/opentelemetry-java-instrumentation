/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.getTableName;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.resetRequestAndContext;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.setRequestAndContext;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4.HbaseSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4.HbaseSingletons.methodDescriptorName;
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
    return named("org.apache.hadoop.hbase.ipc.AbstractRpcClient");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.google.protobuf.Descriptors")
        .or(hasClassesNamed("org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("callMethod")
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "com.google.protobuf.Descriptors$MethodDescriptor",
                        "org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User")))
            .and(takesArgument(5, named("java.net.InetSocketAddress"))),
        getClass().getName() + "$CallMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallMethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static RequestAndContext onEnter(
        @Advice.Argument(0) Object md,
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
      RequestAndContext requestAndContext = RequestAndContext.create(request, scope, context);
      setRequestAndContext(requestAndContext);
      return requestAndContext;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable RequestAndContext requestAndContext) {
      resetRequestAndContext();
      if (requestAndContext == null) {
        return;
      }

      requestAndContext.getScope().close();

      if (throwable != null) {
        instrumenter()
            .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, throwable);
      }
    }
  }
}
