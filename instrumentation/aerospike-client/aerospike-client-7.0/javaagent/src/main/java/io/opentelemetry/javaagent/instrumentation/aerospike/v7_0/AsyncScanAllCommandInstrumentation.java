/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.aerospike.client.async.AsyncScanPartitionExecutor;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Locale;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AsyncScanAllCommandInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.aerospike.client.async.AsyncScanPartitionExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(isPublic()), this.getClass().getName() + "$ConstructorAdvice");
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
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This AsyncScanPartitionExecutor asyncScanPartitionExecutor,
        @Advice.Argument(4) String namespace,
        @Advice.Argument(5) String setName) {
      VirtualField<AsyncScanPartitionExecutor, AerospikeRequestContext> virtualField =
          VirtualField.find(AsyncScanPartitionExecutor.class, AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(asyncScanPartitionExecutor);
      if (requestContext != null) {
        return;
      }
      Context parentContext = currentContext();
      AerospikeRequest request =
          AerospikeRequest.create(
              asyncScanPartitionExecutor.getClass().getSimpleName().toUpperCase(Locale.ROOT),
              namespace,
              setName);
      if (!AersopikeSingletons.instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      Context context = AersopikeSingletons.instrumenter().start(parentContext, request);
      AerospikeRequestContext aerospikeRequestContext =
          AerospikeRequestContext.attach(request, context);

      virtualField.set(asyncScanPartitionExecutor, aerospikeRequestContext);
    }
  }

  @SuppressWarnings("unused")
  public static class OnSuccessAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.This AsyncScanPartitionExecutor asyncScanPartitionExecutor) {
      VirtualField<AsyncScanPartitionExecutor, AerospikeRequestContext> virtualField =
          VirtualField.find(AsyncScanPartitionExecutor.class, AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(asyncScanPartitionExecutor);
      virtualField.set(asyncScanPartitionExecutor, null);
      if (requestContext != null) {
        AerospikeRequest request = requestContext.getRequest();
        Context context = requestContext.getContext();
        if (throwable == null) {
          request.setStatus(Status.SUCCESS);
        } else {
          request.setStatus(Status.FAILURE);
        }
        requestContext.endSpan(AersopikeSingletons.instrumenter(), throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnFailureAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.This AsyncScanPartitionExecutor asyncScanPartitionExecutor) {
      VirtualField<AsyncScanPartitionExecutor, AerospikeRequestContext> virtualField =
          VirtualField.find(AsyncScanPartitionExecutor.class, AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(asyncScanPartitionExecutor);
      virtualField.set(asyncScanPartitionExecutor, null);
      if (requestContext != null) {
        AerospikeRequest request = requestContext.getRequest();
        Context context = requestContext.getContext();
        request.setStatus(Status.FAILURE);
        requestContext.endSpan(AersopikeSingletons.instrumenter(), throwable);
      }
    }
  }
}
