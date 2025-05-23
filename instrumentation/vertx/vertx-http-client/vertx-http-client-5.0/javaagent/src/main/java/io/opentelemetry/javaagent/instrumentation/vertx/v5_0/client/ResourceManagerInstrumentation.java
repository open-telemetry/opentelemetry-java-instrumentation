/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.client;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Propagate context to connection established callback. */
public class ResourceManagerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.internal.resource.ResourceManager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("withResourceAsync").and(returns(named("io.vertx.core.Future"))),
        this.getClass().getName() + "$WithResourceAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class WithResourceAsyncAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void wrapFuture(@Advice.Return(readOnly = false) Future<?> future) {
      future = VertxClientSingletons.wrapFuture(future);
    }
  }
}
