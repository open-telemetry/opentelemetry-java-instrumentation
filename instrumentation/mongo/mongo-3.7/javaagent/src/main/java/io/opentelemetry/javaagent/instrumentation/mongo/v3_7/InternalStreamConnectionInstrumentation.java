/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.async.SingleResultCallback;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class InternalStreamConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.internal.connection.InternalStreamConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("openAsync"))
            .and(takesArgument(0, named("com.mongodb.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg0Advice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("readAsync"))
            .and(takesArgument(1, named("com.mongodb.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg1Advice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("writeAsync"))
            .and(takesArgument(1, named("com.mongodb.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg1Advice");
  }

  @SuppressWarnings("unused")
  public static class SingleResultCallbackArg0Advice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SingleResultCallback<Object> wrapCallback(
        @Advice.Argument(0) SingleResultCallback<Object> callback) {
      return new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }

  @SuppressWarnings("unused")
  public static class SingleResultCallbackArg1Advice {

    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SingleResultCallback<Object> wrapCallback(
        @Advice.Argument(1) SingleResultCallback<Object> callback) {
      return new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }
}
