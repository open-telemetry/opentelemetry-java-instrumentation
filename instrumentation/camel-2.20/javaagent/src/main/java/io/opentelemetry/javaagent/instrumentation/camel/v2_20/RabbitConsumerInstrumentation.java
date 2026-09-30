/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.rabbitmq.client.Consumer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class RabbitConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.camel.component.rabbitmq.RabbitConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("start", "doStart").and(takesArguments(0)),
        getClass().getName() + "$StartAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This Consumer consumer) {
      if (emitStableMessagingSemconv()) {
        CamelRabbitProcessingOwnership.markCamelAsProcessingOwner(consumer);
      }
    }
  }
}
