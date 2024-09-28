/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.messaging.Message;

public class MessagingMessageListenerAdapterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.awspring.cloud.sqs.listener.adapter.MessagingMessageListenerAdapter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onMessage").and(takesArgument(0, named("org.springframework.messaging.Message"))),
        this.getClass().getName() + "$OnMessageAdvice");
    // TODO: onMessage(Collection<Message<T>> messages) not instrumented
  }

  @SuppressWarnings("unused")
  public static class OnMessageAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpringAwsUtil.MessageScope methodEnter(@Advice.Argument(0) Message<?> message) {
      return SpringAwsUtil.handleMessage(message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter SpringAwsUtil.MessageScope scope, @Advice.Thrown Throwable throwable) {
      if (scope != null) {
        scope.close(throwable);
      }
    }
  }
}
