/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.async.SingleResultCallback;
import com.mongodb.connection.ConnectionDescription;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
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
    transformer.applyAdviceToMethod(named("open"), getClass().getName() + "$OpenAdvice");
    transformer.applyAdviceToMethod(
        named("openAsync").and(takesArgument(0, named("com.mongodb.async.SingleResultCallback"))),
        getClass().getName() + "$SingleResultCallbackArg0Advice");
    transformer.applyAdviceToMethod(
        named("readAsync").and(takesArgument(1, named("com.mongodb.async.SingleResultCallback"))),
        getClass().getName() + "$SingleResultCallbackArg1Advice");
    transformer.applyAdviceToMethod(
        named("writeAsync").and(takesArgument(1, named("com.mongodb.async.SingleResultCallback"))),
        getClass().getName() + "$SingleResultCallbackArg1Advice");
  }

  @SuppressWarnings("unused")
  public static class OpenAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static MongoConnectionPeer.OpenState onEnter() {
      return MongoConnectionPeer.startOpen();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter MongoConnectionPeer.OpenState state,
        @Advice.FieldValue("description") @Nullable ConnectionDescription connectionDescription,
        @Advice.Thrown @Nullable Throwable error) {
      MongoConnectionPeer.endOpen(state, connectionDescription, error);
    }
  }

  @SuppressWarnings("unused")
  public static class SingleResultCallbackArg0Advice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static SingleResultCallback<Object> wrapCallback(
        @Advice.Argument(0) SingleResultCallback<Object> callback) {
      return new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }

  @SuppressWarnings("unused")
  public static class SingleResultCallbackArg1Advice {

    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static SingleResultCallback<Object> wrapCallback(
        @Advice.Argument(1) SingleResultCallback<Object> callback) {
      return new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }
}
