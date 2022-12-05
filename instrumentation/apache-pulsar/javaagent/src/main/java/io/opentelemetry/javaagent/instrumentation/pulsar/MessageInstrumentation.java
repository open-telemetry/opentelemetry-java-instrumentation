/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;

public class MessageInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.impl.MessageImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("recycle")).and(takesArguments(0)),
        MessageInstrumentation.class.getName() + "$MessageRecycleAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageRecycleAdvice {
    private MessageRecycleAdvice() {}

    @Advice.OnMethodExit
    public static void after(@Advice.This Message<?> message) {
      // Clean context to prevent memory leak.
      VirtualFieldStore.inject(message, null);
    }
  }
}
