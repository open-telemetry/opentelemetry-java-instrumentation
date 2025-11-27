/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import static io.opentelemetry.instrumentation.thrift.common.client.VirtualFields.ASYNC_METHOD_CALLBACK;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.AsyncMethodCallbackWrapper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;

public final class ThriftAsyncMethodCallInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.async.TAsyncMethodCall");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(3, named("org.apache.thrift.async.AsyncMethodCallback"))),
        ThriftAsyncMethodCallInstrumentation.class.getName() + "$ConstructorAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(named("prepareMethodCall")),
        ThriftAsyncMethodCallInstrumentation.class.getName() + "$MethodCallAdvice");
  }

  public static class ConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 3, readOnly = false) AsyncMethodCallback<?> callback) {
      if (callback instanceof AsyncMethodCallbackWrapper) {
        return;
      }
      callback = new AsyncMethodCallbackWrapper<>(callback, false);
    }
  }

  public static class MethodCallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This TAsyncMethodCall<?> thiz,
        @Advice.FieldValue(value = "callback") AsyncMethodCallback<?> callback) {
      if (callback instanceof AsyncMethodCallbackWrapper) {
        ASYNC_METHOD_CALLBACK.set(thiz, callback);
      }
    }
  }
}
