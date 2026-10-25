/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class MessageCryptoInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.pulsar.client.api.MessageCrypto"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getMaxOutputSize")
            .and(takesArguments(int.class))
            .or(
                named("encrypt")
                    .and(takesArgument(0, named("java.util.Set")))
                    .and(takesArgument(1, named("org.apache.pulsar.client.api.CryptoKeyReader")))
                    .and(takesArgument(2, named("java.util.function.Supplier")))
                    .and(takesArgument(3, named("java.nio.ByteBuffer")))
                    .and(takesArgument(4, named("java.nio.ByteBuffer")))),
        getClass().getName() + "$CryptoAdvice");
  }

  @SuppressWarnings("unused")
  public static class CryptoAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter() {
      SendCallbackData data = SendCallbackData.current();
      return data == null ? null : data.parentContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
