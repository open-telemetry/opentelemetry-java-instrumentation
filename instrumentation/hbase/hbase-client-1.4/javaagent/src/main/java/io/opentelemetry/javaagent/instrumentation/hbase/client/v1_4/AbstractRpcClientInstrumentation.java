/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.resetRequestAndContext;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.setRequestAndContext;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientUtil.createRequest;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4.HbaseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
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
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.google.protobuf.Descriptors")
        .or(hasClassesNamed("org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors"));
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
                    namedOneOf(
                        "com.google.protobuf.Descriptors$MethodDescriptor",
                        "org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors$MethodDescriptor")))
            .and(takesArgument(4, named("org.apache.hadoop.hbase.security.User")))
            .and(takesArgument(5, InetSocketAddress.class)),
        getClass().getName() + "$CallMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This AbstractRpcClient<?> client, @Advice.Argument(0) Configuration configuration) {
      String serverTarget = HbaseServerTarget.from(configuration);
      if (serverTarget != null) {
        VirtualField.find(AbstractRpcClient.class, String.class).set(client, serverTarget);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CallMethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static RequestAndContext onEnter(
        @Advice.This AbstractRpcClient<?> client,
        @Advice.Argument(0) Object md,
        @Advice.Argument(2) Object param,
        @Advice.Argument(4) User ticket,
        @Advice.Argument(5) InetSocketAddress addr) {
      String serverTarget = VirtualField.find(AbstractRpcClient.class, String.class).get(client);
      HbaseRequest request = createRequest(md, param, ticket, addr, serverTarget);
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
