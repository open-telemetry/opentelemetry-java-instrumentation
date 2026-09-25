/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Subscriber;
import reactor.core.CoreSubscriber;

public class LettuceReactiveSubscriptionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.RedisPublisher$RedisSubscription");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("subscribe").and(takesArguments(1)), getClass().getName() + "$SubscribeAdvice");
  }

  @SuppressWarnings("unused")
  public static class SubscribeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @AssignReturned.ToArguments(@ToArgument(0))
    public static Subscriber<?> onEnter(
        @Advice.FieldValue("command") RedisCommand<?, ?, ?> command,
        @Advice.Argument(0) Subscriber<?> subscriber) {
      if (subscriber instanceof CoreSubscriber<?>) {
        LettuceReactiveCommandHandler handler =
            LettuceReactiveCommandContext.handler((CoreSubscriber<?>) subscriber);
        if (handler != null) {
          handler.onCommand(command);
          return LettuceReactiveCommandSubscriber.withCancellation(
              (CoreSubscriber<?>) subscriber, handler);
        }
      }
      return subscriber;
    }
  }
}
