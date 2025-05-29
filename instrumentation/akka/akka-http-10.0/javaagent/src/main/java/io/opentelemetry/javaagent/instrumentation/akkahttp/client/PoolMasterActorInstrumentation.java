/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PoolMasterActorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.impl.engine.client.PoolMasterActor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // scala compiler mangles method names
    transformer.applyAdviceToMethod(
        namedOneOf(
            "akka$http$impl$engine$client$PoolMasterActor$$startPoolInterface",
            "akka$http$impl$engine$client$PoolMasterActor$$startPoolInterfaceActor"),
        ClearContextAdvice.class.getName());
  }

  @SuppressWarnings("unused")
  public static class ClearContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter() {
      return Java8BytecodeBridge.rootContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
