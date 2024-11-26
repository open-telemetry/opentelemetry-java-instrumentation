/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.messaging.Message;

public class AbstractMessageConvertingMessageSourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.awspring.cloud.sqs.listener.source.AbstractMessageConvertingMessageSource");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("convertMessages")
            .and(takesArgument(0, Collection.class))
            .and(returns(Collection.class)),
        this.getClass().getName() + "$ConvertMessagesAdvice");
    transformer.applyAdviceToMethod(
        named("convertMessage")
            .and(takesArgument(0, Object.class))
            .and(returns(named("org.springframework.messaging.Message"))),
        this.getClass().getName() + "$ConvertAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConvertMessagesAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.Argument(0) Collection<?> messages) {
      SpringAwsUtil.initialize(messages);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit() {
      SpringAwsUtil.clear();
    }
  }

  @SuppressWarnings("unused")
  public static class ConvertAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Object originalMessage, @Advice.Return Message<?> convertedMessage) {
      SpringAwsUtil.attachTracingState(originalMessage, convertedMessage);
    }
  }
}
