/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Flux;

// handles versions 1.3.+
public class ConsumerHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.kafka.receiver.internals.ConsumerHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receive").and(returns(named("reactor.core.publisher.Flux"))),
        this.getClass().getName() + "$ReceiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Flux<?> onExit(@Advice.Return Flux<?> flux) {
      if (flux instanceof TracingDisablingKafkaFlux) {
        return flux;
      }
      return new TracingDisablingKafkaFlux<>(flux);
    }
  }
}
