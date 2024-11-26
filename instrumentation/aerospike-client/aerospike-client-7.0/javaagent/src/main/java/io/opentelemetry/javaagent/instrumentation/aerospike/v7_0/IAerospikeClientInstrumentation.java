/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.AersopikeSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.internal.CustomElementMatcher.argumentOfType;
import static io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.internal.CustomElementMatcher.iterableHasAtLeastOne;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesGenericArguments;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.async.EventLoop;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.internal.AerospikeRequest;
import io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.internal.AerospikeRequestContext;
import java.util.Locale;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IAerospikeClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("com.aerospike.client.IAerospikeClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(takesGenericArguments(iterableHasAtLeastOne(argumentOfType(Key.class))))
            .and(
                not(takesGenericArguments(iterableHasAtLeastOne(argumentOfType(EventLoop.class))))),
        this.getClass().getName() + "$SyncCommandAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(takesGenericArguments(iterableHasAtLeastOne(argumentOfType(Key.class))))
            .and(takesGenericArguments(iterableHasAtLeastOne(argumentOfType(EventLoop.class)))),
        this.getClass().getName() + "$AsyncCommandAdvice");
  }

  @SuppressWarnings("unused")
  public static class SyncCommandAdvice {

    @Advice.OnMethodEnter(suppress = AerospikeException.class)
    public static void startInstrumentation(
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] args,
        @Advice.Local("AerospikeContext") AerospikeRequestContext requestContext) {
      Key key = null;
      for (Object object : args) {
        if (object instanceof Key) {
          key = (Key) object;
        }
      }
      if (key == null) {
        return;
      }
      Context parentContext = currentContext();
      AerospikeRequest request =
          AerospikeRequest.create(methodName.toUpperCase(Locale.ROOT), key.namespace, key.setName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      Context context = instrumenter().start(parentContext, request);
      requestContext = AerospikeRequestContext.attach(request, context);
    }

    @Advice.OnMethodExit(
        onThrowable = AerospikeException.class,
        suppress = AerospikeException.class)
    public static void stopInstrumentation(
        @Advice.Thrown AerospikeException ae,
        @Advice.Local("AerospikeContext") AerospikeRequestContext requestContext) {
      requestContext.setThrowable(ae);
      requestContext.detachAndEnd();
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncCommandAdvice {

    @Advice.OnMethodEnter(suppress = AerospikeException.class)
    public static void startInstrumentation(
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] args,
        @Advice.Local("AerospikeContext") AerospikeRequestContext requestContext) {
      Key key = null;
      for (Object object : args) {
        if (object instanceof Key) {
          key = (Key) object;
        }
      }
      if (key == null) {
        return;
      }
      Context parentContext = currentContext();
      AerospikeRequest request =
          AerospikeRequest.create(methodName.toUpperCase(Locale.ROOT), key.namespace, key.setName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      Context context = instrumenter().start(parentContext, request);
      requestContext = AerospikeRequestContext.attach(request, context);
    }

    @Advice.OnMethodExit(
        onThrowable = AerospikeException.class,
        suppress = AerospikeException.class)
    public static void stopInstrumentaionIfError(
        @Advice.Thrown AerospikeException ae,
        @Advice.Local("AerospikeContext") AerospikeRequestContext requestContext) {
      if (ae != null) {
        requestContext.setThrowable(ae);
        requestContext.detachAndEnd();
      }
    }
  }
}
