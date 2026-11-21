/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class AbstractSqsMessageSourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.awspring.cloud.sqs.listener.source.AbstractSqsMessageSource");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("combineBatches").and(takesArguments(Collection.class, Collection.class)),
        getClass().getName() + "$CombineBatchesAdvice");
  }

  @SuppressWarnings("unused")
  public static class CombineBatchesAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void methodEnter(
        @Advice.Argument(0) Collection<?> firstBatch,
        @Advice.Argument(1) Collection<?> secondBatch) {
      SpringAwsUtil.carryTracingState(firstBatch);
      SpringAwsUtil.carryTracingState(secondBatch);
    }
  }
}
