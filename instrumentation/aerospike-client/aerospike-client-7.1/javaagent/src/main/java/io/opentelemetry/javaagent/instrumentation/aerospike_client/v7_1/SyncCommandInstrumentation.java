/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.AersopikeSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.Status.FAILURE;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.Status.RECORD_NOT_FOUND;
import static io.opentelemetry.javaagent.instrumentation.aerospike_client.v7_1.Status.SUCCESS;
import static net.bytebuddy.matcher.ElementMatchers.isFinal;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.aerospike.client.Key;
import com.aerospike.client.Record;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Locale;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SyncCommandInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.aerospike.client.AerospikeClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(not(isStatic())).and(isFinal())
            .and(takesArgument(1, named("com.aerospike.client.Key")))
            .and(not(takesArgument(0, named("com.aerospike.client.async.EventLoop"))))
            .and(returns(named("com.aerospike.client.Record"))),
        this.getClass().getName() + "$SyncReturnCommandAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(not(isStatic())).and(isFinal())
            .and(takesArgument(1, named("com.aerospike.client.Key")))
            .and(not(takesArgument(0, named("com.aerospike.client.async.EventLoop"))))
            .and(not(returns(named("com.aerospike.client.Record")))),
        this.getClass().getName() + "$SyncNonReturnCommandAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(not(isStatic())).and(isFinal()).and(named("scanAll"))
            .and(not(takesArgument(0, named("com.aerospike.client.async.EventLoop")))),
        this.getClass().getName() + "$SyncScanAllCommandAdvice");
  }

  @SuppressWarnings("unused")
  public static class SyncReturnCommandAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AerospikeRequestContext onEnter(
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] keys,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      Key key = null;
      for (Object object : keys) {
        if (object instanceof Key) {
          key = (Key) object;
        }
      }
      if (key == null) {
        return null;
      }
      request = AerospikeRequest.create(methodName.toUpperCase(Locale.ROOT), key);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
      return AerospikeRequestContext.attach(request, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Return Record record,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter AerospikeRequestContext requestContext,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (throwable != null) {
        request.setStatus(FAILURE);
      } else if (record == null) {
        request.setStatus(RECORD_NOT_FOUND);
      } else {
        request.setStatus(SUCCESS);
      }
      if (scope == null) {
        return;
      }

      scope.close();
      if (requestContext != null) {
        requestContext.endSpan(instrumenter(), context, request, throwable);
        requestContext.detachAndEnd();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SyncNonReturnCommandAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AerospikeRequestContext onEnter(
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] keys,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      Key key = null;
      for (Object object : keys) {
        if (object instanceof Key) {
          key = (Key) object;
        }
      }
      if (key == null) {
        return null;
      }
      request = AerospikeRequest.create(methodName.toUpperCase(Locale.ROOT), key);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
      return AerospikeRequestContext.attach(request, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static AerospikeRequestContext stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Enter AerospikeRequestContext requestContext,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (throwable != null) {
        request.setStatus(FAILURE);
      } else {
        request.setStatus(SUCCESS);
      }
      if (scope == null) {
        return requestContext;
      }

      scope.close();
      if (requestContext != null) {
        requestContext.endSpan(instrumenter(), context, request, throwable);
        requestContext.detachAndEnd();
      }
      return requestContext;
    }
  }

  @SuppressWarnings("unused")
  public static class SyncScanAllCommandAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AerospikeRequestContext onEnter(
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(1) String namespace,
        @Advice.Argument(2) String setName,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();

      request = AerospikeRequest.create(methodName.toUpperCase(Locale.ROOT), namespace, setName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
      return AerospikeRequestContext.attach(request, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static AerospikeRequestContext stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Enter AerospikeRequestContext requestContext,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (throwable != null) {
        request.setStatus(FAILURE);
      } else {
        request.setStatus(SUCCESS);
      }
      if (scope == null) {
        return requestContext;
      }

      scope.close();
      if (requestContext != null) {
        requestContext.endSpan(instrumenter(), context, request, throwable);
        requestContext.detachAndEnd();
      }
      return requestContext;
    }
  }
}
