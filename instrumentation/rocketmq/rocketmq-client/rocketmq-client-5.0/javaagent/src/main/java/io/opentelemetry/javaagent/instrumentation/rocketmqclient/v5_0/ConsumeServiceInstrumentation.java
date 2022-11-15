/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.apis.consumer.MessageListener;

final class ConsumeServiceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.java.impl.consumer.ConsumeService");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                isPublic()
                    .and(
                        takesArgument(
                            1, named("org.apache.rocketmq.client.apis.consumer.MessageListener")))),
        ConsumeServiceInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 1, readOnly = false) MessageListener messageListener) {
      // Replace messageListener by wrapper.
      if (!(messageListener instanceof MessageListenerWrapper)) {
        messageListener = new MessageListenerWrapper(messageListener);
      }
    }
  }
}
