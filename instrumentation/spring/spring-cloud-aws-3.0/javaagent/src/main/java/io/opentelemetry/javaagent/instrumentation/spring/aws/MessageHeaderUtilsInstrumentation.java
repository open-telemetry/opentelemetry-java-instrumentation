/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.messaging.Message;

public class MessageHeaderUtilsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.awspring.cloud.sqs.MessageHeaderUtils");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("addHeaderIfAbsent", "addHeadersIfAbsent", "removeHeaderIfPresent")
            .and(returns(named("org.springframework.messaging.Message")))
            .and(takesArgument(0, named("org.springframework.messaging.Message"))),
        this.getClass().getName() + "$PreserveContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class PreserveContextAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Message<?> original, @Advice.Return Message<?> result) {
      SpringAwsUtil.copyTracingState(original, result);
    }
  }
}
