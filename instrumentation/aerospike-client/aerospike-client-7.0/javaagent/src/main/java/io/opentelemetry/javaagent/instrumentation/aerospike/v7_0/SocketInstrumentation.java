/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import static io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.AersopikeSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.Status.FAILURE;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.aerospike.client.cluster.Node;
import com.aerospike.client.command.Command;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SocketInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperClass(named("com.aerospike.client.command.Command"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
            .and(named("getNode"))
            .and(returns(named("com.aerospike.client.cluster.Node")))
            .and(takesNoArguments()),
        this.getClass().getName() + "$NodeSyncCommandAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("getNode"))
            .and(returns(named("com.aerospike.client.cluster.Node")))
            .and(takesArgument(0, named("com.aerospike.client.cluster.Cluster"))),
        this.getClass().getName() + "$NodeAsyncCommandAdvice");
  }

  @SuppressWarnings("unused")
  public static class NodeSyncCommandAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(@Advice.Return Node node) {
      AerospikeRequestContext context = AerospikeRequestContext.current();
      if (context != null) {
        AerospikeRequest request = context.getRequest();
        request.setNode(node);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class NodeAsyncCommandAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Return Node node,
        @Advice.This Command command) {
      VirtualField<Command, AerospikeRequestContext> virtualField =
          VirtualField.find(Command.class, AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(command);
      if (requestContext != null) {
        AerospikeRequest request = requestContext.getRequest();
        request.setNode(node);
        if (throwable != null) {
          request.setStatus(FAILURE);
          requestContext.endSpan(instrumenter(), throwable);
          requestContext.detachAndEnd();
        }
      }
    }
  }
}
