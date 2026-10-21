/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8.RocketMqSingletons.batchSendHelper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.common.message.Message;

class RocketMqMessageBatchInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.common.message.MessageBatch");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("encode").and(takesArguments(0)), getClass().getName() + "$EncodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class EncodeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This Message batch) {
      batchSendHelper().beforeBatchEncode(batch);
    }
  }
}
