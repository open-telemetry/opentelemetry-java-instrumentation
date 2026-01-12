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
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
    // since 5.6.0
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(4, Consumer.class)),
        this.getClass().getName() + "$TaskArg4Advice");
  }

  @SuppressWarnings("unused")
  public static class TaskArg2Advice {

    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Consumer<Object> wrapCallback(@Advice.Argument(2) Consumer<Object> action) {
      return new TaskWrapper(Java8BytecodeBridge.currentContext(), action);
    }
  }

  @SuppressWarnings("unused")
  public static class TaskArg3Advice {

    @AssignReturned.ToArguments(@ToArgument(3))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Consumer<Object> wrapCallback(@Advice.Argument(3) Consumer<Object> action) {
      return new TaskWrapper(Java8BytecodeBridge.currentContext(), action);
    }
  }

  @SuppressWarnings("unused")
  public static class TaskArg4Advice {

    @AssignReturned.ToArguments(@ToArgument(4))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Consumer<Object> wrapCallback(@Advice.Argument(4) Consumer<Object> action) {
      return new TaskWrapper(Java8BytecodeBridge.currentContext(), action);
    }
  }
}
