/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.AersopikeSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.Status.FAILURE;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.Status.SUCCESS;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.aerospike.client.async.AsyncScanPartitionExecutor;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
        isConstructor()
            .and(isPublic()),
        this.getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("onSuccess")
            .and(takesNoArguments())
            .and(isProtected()),
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
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Argument(4) String namespace,
        @Advice.Argument(5) String setName) {
      VirtualField<AsyncScanPartitionExecutor, AerospikeRequestContext> virtualField = VirtualField.find(
          AsyncScanPartitionExecutor.class,
          AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(asyncScanPartitionExecutor);
      if (requestContext != null) {
        return;
      }
      Context parentContext = currentContext();
      request = AerospikeRequest.create(
          asyncScanPartitionExecutor.getClass().getSimpleName().toUpperCase(Locale.ROOT),
          namespace, setName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      context = instrumenter().start(parentContext, request);
      AerospikeRequestContext aerospikeRequestContext = AerospikeRequestContext.attach(request,
          context);
      scope = context.makeCurrent();

      virtualField.set(asyncScanPartitionExecutor, aerospikeRequestContext);
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnSuccessAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Thrown Throwable throwable,
        @Advice.This AsyncScanPartitionExecutor asyncScanPartitionExecutor) {
      VirtualField<AsyncScanPartitionExecutor, AerospikeRequestContext> virtualField = VirtualField.find(
          AsyncScanPartitionExecutor.class,
          AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(asyncScanPartitionExecutor);
      virtualField.set(asyncScanPartitionExecutor, null);
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
    public static void onExit(@Advice.Thrown Throwable throwable,
        @Advice.This AsyncScanPartitionExecutor asyncScanPartitionExecutor) {
      VirtualField<AsyncScanPartitionExecutor, AerospikeRequestContext> virtualField = VirtualField.find(
          AsyncScanPartitionExecutor.class,
          AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(asyncScanPartitionExecutor);
      virtualField.set(asyncScanPartitionExecutor, null);
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
