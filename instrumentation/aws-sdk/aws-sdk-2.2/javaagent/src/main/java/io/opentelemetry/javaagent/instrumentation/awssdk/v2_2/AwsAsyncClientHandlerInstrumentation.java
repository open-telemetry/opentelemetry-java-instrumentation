/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AwsAsyncClientHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // This class is used internally by the aws async clients to execute requests. Alternatively
    // we could instrument all methods that return a CompletableFuture in classes whose name ends
    // with "AsyncClient" that extend software.amazon.awssdk.core.SdkClient
    return named("software.amazon.awssdk.awscore.client.handler.AwsAsyncClientHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(returns(CompletableFuture.class)),
        this.getClass().getName() + "$WrapFutureAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapFutureAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) CompletableFuture<?> future) {
      // propagate context into CompletableFuture returned from aws async client methods
      future = CompletableFutureWrapper.wrap(future);
    }
  }
}
