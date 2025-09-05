/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Propagate context to connection established callback. */
public class ConnectionManagerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.vertx.core.net.impl.clientconnection.ConnectionManager", // 4.0.0
        "io.vertx.core.net.impl.pool.ConnectionManager"); // 4.1.0
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getConnection").and(takesArgument(2, named("io.vertx.core.Handler"))),
        ConnectionManagerInstrumentation.class.getName() + "$GetConnectionArg2Advice");
    transformer.applyAdviceToMethod(
        named("getConnection").and(takesArgument(3, named("io.vertx.core.Handler"))),
        ConnectionManagerInstrumentation.class.getName() + "$GetConnectionArg3Advice");
    // since 4.3.4
    transformer.applyAdviceToMethod(
        named("getConnection").and(takesArgument(4, named("io.vertx.core.Handler"))),
        ConnectionManagerInstrumentation.class.getName() + "$GetConnectionArg4Advice");
  }

  @SuppressWarnings("unused")
  public static class GetConnectionArg2Advice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Handler<?> wrapHandler(@Advice.Argument(2) @Nullable Handler<?> handler) {
      return HandlerWrapper.wrap(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionArg3Advice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(3))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Handler<?> wrapHandler(@Advice.Argument(3) @Nullable Handler<?> handler) {
      return HandlerWrapper.wrap(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionArg4Advice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(4))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Handler<?> wrapHandler(@Advice.Argument(4) @Nullable Handler<?> handler) {
      return HandlerWrapper.wrap(handler);
    }
  }
}
