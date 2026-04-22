/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import static io.opentelemetry.instrumentation.thrift.common.client.VirtualFields.ASYNC_METHOD_CALLBACK;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.AsyncMethodCallbackWrapper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;

class ThriftAsyncMethodCallInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.async.TAsyncMethodCall");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(3, named("org.apache.thrift.async.AsyncMethodCallback"))),
        getClass().getName() + "$ConstructorAdvice");

    transformer.applyAdviceToMethod(
        named("prepareMethodCall"), getClass().getName() + "$MethodCallAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @AssignReturned.ToArguments(@ToArgument(3))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AsyncMethodCallback<?> onEnter(
        @Advice.Argument(3) AsyncMethodCallback<?> callback) {
      if (callback instanceof AsyncMethodCallbackWrapper) {
        return callback;
      }
      return new AsyncMethodCallbackWrapper<>(callback, false);
    }
  }

  @SuppressWarnings("unused")
  public static class MethodCallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void methodEnter(
        @Advice.This TAsyncMethodCall<?> thiz,
        @Advice.FieldValue("callback") AsyncMethodCallback<?> callback) {
      if (callback instanceof AsyncMethodCallbackWrapper) {
        ASYNC_METHOD_CALLBACK.set(thiz, callback);
      }
    }
  }
}
