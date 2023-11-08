/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1;

import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.AersopikeSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.Status.FAILURE;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.Status.SUCCESS;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.aerospike.client.command.Command;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AsyncHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperClass(named("com.aerospike.client.command.Command"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onSuccess").and(takesNoArguments()).and(isProtected()),
        this.getClass().getName() + "$OnSuccessAdvice");
    transformer.applyAdviceToMethod(
        named("onFailure")
            .and(takesArgument(0, named("com.aerospike.client.AerospikeException")))
            .and(isProtected()),
        this.getClass().getName() + "$OnFailureAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnSuccessAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Thrown Throwable throwable, @Advice.This Command command) {
      VirtualField<Command, AerospikeRequestContext> virtualField =
          VirtualField.find(Command.class, AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(command);
      virtualField.set(command, null);
      if (requestContext != null) {
        AerospikeRequest request = requestContext.getRequest();
        Context context = requestContext.getContext();
        if (throwable == null) {
          request.setStatus(SUCCESS);
        } else {
          request.setStatus(FAILURE);
        }
        requestContext.endSpan(instrumenter(), context, request, throwable);
        Scope scope = context.makeCurrent();
        if (null != scope) {
          scope.close();
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnFailureAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Thrown Throwable throwable, @Advice.This Command command) {
      VirtualField<Command, AerospikeRequestContext> virtualField =
          VirtualField.find(Command.class, AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(command);
      virtualField.set(command, null);
      if (requestContext != null) {
        AerospikeRequest request = requestContext.getRequest();
        Context context = requestContext.getContext();
        request.setStatus(FAILURE);
        requestContext.endSpan(instrumenter(), context, request, throwable);
        Scope scope = context.makeCurrent();
        if (null != scope) {
          scope.close();
        }
      }
    }
  }
}
