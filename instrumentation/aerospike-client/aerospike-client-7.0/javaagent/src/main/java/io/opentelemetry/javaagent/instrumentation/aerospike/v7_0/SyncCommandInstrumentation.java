/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
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
            .and(not(isStatic()))
            .and(isFinal())
            .and(takesArgument(1, named("com.aerospike.client.Key")))
            .and(not(takesArgument(0, named("com.aerospike.client.async.EventLoop"))))
            .and(returns(named("com.aerospike.client.Record"))),
        this.getClass().getName() + "$SyncReturnCommandAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(not(isStatic()))
            .and(isFinal())
            .and(takesArgument(1, named("com.aerospike.client.Key")))
            .and(not(takesArgument(0, named("com.aerospike.client.async.EventLoop"))))
            .and(not(returns(named("com.aerospike.client.Record")))),
        this.getClass().getName() + "$SyncNonReturnCommandAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(not(isStatic()))
            .and(isFinal())
            .and(named("scanAll"))
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
      if (!AersopikeSingletons.instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      context = AersopikeSingletons.instrumenter().start(parentContext, request);
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
        request.setStatus(Status.FAILURE);
      } else if (record == null) {
        request.setStatus(Status.RECORD_NOT_FOUND);
      } else {
        request.setStatus(Status.SUCCESS);
      }
      if (record == null || record.bins.isEmpty()) {
        request.setSize(0);
      } else {
        int size = 0;
        for (Object value : record.bins.values()) {
          size += ((String) value).length();
        }
        request.setSize(size);
      }
      if (scope == null) {
        return;
      }

      scope.close();
      if (requestContext != null) {
        requestContext.endSpan(AersopikeSingletons.instrumenter(), throwable);
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
      if (!AersopikeSingletons.instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      context = AersopikeSingletons.instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
      return AerospikeRequestContext.attach(request, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Enter AerospikeRequestContext requestContext,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (throwable != null) {
        request.setStatus(Status.FAILURE);
      } else {
        request.setStatus(Status.SUCCESS);
      }
      if (scope != null) {
        scope.close();
      }

      if (requestContext != null) {
        requestContext.endSpan(AersopikeSingletons.instrumenter(), throwable);
        requestContext.detachAndEnd();
      }
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
      if (!AersopikeSingletons.instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      context = AersopikeSingletons.instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
      return AerospikeRequestContext.attach(request, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Enter AerospikeRequestContext requestContext,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (throwable != null) {
        request.setStatus(Status.FAILURE);
      } else {
        request.setStatus(Status.SUCCESS);
      }
      if (scope != null) {
        scope.close();
      }
      if (requestContext != null) {
        requestContext.endSpan(AersopikeSingletons.instrumenter(), throwable);
        requestContext.detachAndEnd();
      }
    }
  }
}
