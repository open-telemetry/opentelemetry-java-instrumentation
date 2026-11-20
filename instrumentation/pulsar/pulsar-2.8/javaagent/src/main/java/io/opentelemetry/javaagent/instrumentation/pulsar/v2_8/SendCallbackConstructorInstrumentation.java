/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SendCallbackConstructorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("org.apache.pulsar.client.impl.ProducerImpl$")
        .and(hasSuperType(named("org.apache.pulsar.client.impl.SendCallback")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.AssignReturned.ToAllArguments(index = 0)
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.AllArguments Object[] arguments) {
      SendCallbackData data = SendCallbackData.current();
      if (data != null) {
        for (int i = 0; i < arguments.length; i++) {
          if (arguments[i] instanceof CompletableFuture) {
            // The callback, flushAsync's lastSendFuture, and the caller must share one future.
            arguments[i] = data.createSendFuture();
          }
        }
      }
      return new Object[] {arguments};
    }
  }
}
