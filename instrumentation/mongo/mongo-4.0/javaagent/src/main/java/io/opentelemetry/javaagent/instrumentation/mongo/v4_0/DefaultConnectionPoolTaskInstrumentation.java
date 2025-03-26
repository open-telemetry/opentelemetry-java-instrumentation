/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DefaultConnectionPoolTaskInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.internal.connection.DefaultConnectionPool$Task");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // outer class this is passed as arg 0 to constructor
    // before 5.2.0
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(2, Consumer.class)),
        this.getClass().getName() + "$TaskArg2Advice");
    // since 5.2.0
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(3, Consumer.class)),
        this.getClass().getName() + "$TaskArg3Advice");
  }

  @SuppressWarnings("unused")
  public static class TaskArg2Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 2, readOnly = false) Consumer<Object> action) {
      action = new TaskWrapper(Java8BytecodeBridge.currentContext(), action);
    }
  }

  @SuppressWarnings("unused")
  public static class TaskArg3Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 3, readOnly = false) Consumer<Object> action) {
      action = new TaskWrapper(Java8BytecodeBridge.currentContext(), action);
    }
  }
}
