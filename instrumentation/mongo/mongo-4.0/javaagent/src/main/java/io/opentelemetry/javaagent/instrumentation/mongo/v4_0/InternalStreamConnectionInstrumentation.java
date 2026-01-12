/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.internal.async.SingleResultCallback;
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
    // before 5.2.0
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("openAsync"))
            .and(takesArgument(0, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg0Advice");
    // since 5.2.0
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("openAsync"))
            .and(takesArgument(1, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg1Advice");
    // before 5.2.0
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("readAsync"))
            .and(takesArgument(1, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg1Advice");
    // since 5.2.0
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("readAsync"))
            .and(takesArgument(2, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg2Advice");
    // removed in 5.2.0
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("writeAsync"))
            .and(takesArgument(1, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg1Advice");
    // since 5.2.0, earlier versions instrument writeAsync instead
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("sendMessageAsync"))
            .and(takesArgument(3, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackArg3Advice");
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

  @SuppressWarnings("unused")
  public static class SingleResultCallbackArg2Advice {

    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SingleResultCallback<Object> wrapCallback(
        @Advice.Argument(2) SingleResultCallback<Object> callback) {
      return new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }

  @SuppressWarnings("unused")
  public static class SingleResultCallbackArg3Advice {

    @AssignReturned.ToArguments(@ToArgument(3))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SingleResultCallback<Object> wrapCallback(
        @Advice.Argument(3) SingleResultCallback<Object> callback) {
      return new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }
}
