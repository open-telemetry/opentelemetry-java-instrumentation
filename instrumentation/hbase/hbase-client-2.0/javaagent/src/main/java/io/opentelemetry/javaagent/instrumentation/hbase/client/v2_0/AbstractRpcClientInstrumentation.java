/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.currentRequestAndContext;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.getTableName;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ipc.AbstractRpcClient;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos;
import org.apache.hbase.thirdparty.com.google.protobuf.Descriptors;
import org.apache.hbase.thirdparty.com.google.protobuf.Message;

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

    transformer.applyAdviceToMethod(
        named("callMethod")
            .and(
                takesArgument(
                    0,
                    named(
                        "org.apache.hbase.thirdparty.com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User"))),
        getClass().getName() + "$CallMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This AbstractRpcClient<?> client, @Advice.Argument(0) Configuration configuration) {
      HbaseServerTarget.store(client, configuration);
    }
  }

  @SuppressWarnings("unused")
  public static class CallMethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static RequestAndContext onEnter(
        @Advice.This AbstractRpcClient<?> client,
        @Advice.Argument(0) Descriptors.MethodDescriptor md,
        @Advice.Argument(2) Message param)
        throws Throwable {
      String operation = md.getName();
      Long batchSize = null;
      if (param instanceof ClientProtos.MultiRequest) {
        HbaseBatchMetadata batchMetadata =
            HbaseBatchMetadata.create((ClientProtos.MultiRequest) param);
        operation = batchMetadata.getOperation();
        batchSize = batchMetadata.getOperationBatchSize();
      }
      HbaseRequest request =
          HbaseRequest.create(operation, getTableName(), HbaseServerTarget.get(client), batchSize);
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      Scope scope = context.makeCurrent();
      try {
        RequestAndContext requestAndContext = RequestAndContext.create(request, scope, context);
        requestAndContext.setPrevious(currentRequestAndContext().set(requestAndContext));
        return requestAndContext;
      } catch (Throwable t) {
        scope.close();
        instrumenter().end(context, request, null, t);
        throw t;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable RequestAndContext requestAndContext) {
      if (requestAndContext == null) {
        return;
      }

      currentRequestAndContext().restore(requestAndContext.getPrevious());
      requestAndContext.setPrevious(null);
      Scope scope = requestAndContext.getScope();
      scope.close();

      if (throwable != null) {
        instrumenter()
            .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, throwable);
      }
    }
  }
}
