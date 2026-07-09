/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.getBatchMetadata;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.getTableName;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.resetRequestAndContext;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.setRequestAndContext;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.net.Address;
import org.apache.hadoop.hbase.security.User;
import org.apache.hbase.thirdparty.com.google.protobuf.Descriptors;

class AbstractRpcClientInstrumentation implements TypeInstrumentation {

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
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User"))),
        getClass().getName() + "$CallMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallMethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static RequestAndContext onEnter(
        @Advice.Argument(0) Descriptors.MethodDescriptor md,
        @Advice.Argument(4) User ticket,
        @Advice.Argument(5) Object addr) {
      String hostname = null;
      Integer port = null;
      if (addr instanceof Address) {
        Address address = (Address) addr;
        port = address.getPort();
        hostname = address.getHostname();
      } else if (addr instanceof InetSocketAddress) {
        InetSocketAddress address = (InetSocketAddress) addr;
        port = address.getPort();
        hostname = address.getHostString();
      }
      String operation = md.getName();
      Long batchSize = null;
      Context parentContext = Java8BytecodeBridge.currentContext();
      // A Table.batch(...) call is issued as a "Multi" RPC. When batch metadata is propagated in
      // the
      // context, report the derived batch operation name and db.operation.batch.size instead.
      if ("Multi".equals(operation)) {
        HbaseBatchMetadata batchMetadata = getBatchMetadata(parentContext);
        if (batchMetadata != null) {
          operation = batchMetadata.getOperation();
          batchSize = batchMetadata.getOperationBatchSize();
        }
      }
      HbaseRequest request =
          HbaseRequest.create(
              operation, getTableName(), ticket.getName(), hostname, port, batchSize);
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

      Scope scope = requestAndContext.getScope();
      scope.close();

      if (throwable != null) {
        instrumenter()
            .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, throwable);
      }
    }
  }
}
