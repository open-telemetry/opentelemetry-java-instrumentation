/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_21;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.nats.v2_21.NatsSingletons.CONSUMER_INSTRUMENTER;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.nats.v2_21.internal.NatsData;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SubscriptionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.nats.client.Subscription"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("nextMessage"))
            .and(takesArguments(1))
            .and(returns(named("io.nats.client.Message"))),
        SubscriptionInstrumentation.class.getName() + "$NextMessageAdvice");
    transformer.applyAdviceToMethod(
        isPublic().and(named("unsubscribe")),
        SubscriptionInstrumentation.class.getName() + "$UnsubscribeAdvice");
  }

  @SuppressWarnings("unused")
  public static class NextMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Timer onEnter() {
      return Timer.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.This Subscription subscription,
        @Advice.Enter Timer timer,
        @Advice.Return @Nullable Message message) {
      Context parentContext = Context.current();
      TimeoutException timeout = null;

      Connection connection = NatsData.getConnection(subscription);

      // connection should always be non-null at this stage
      if (connection == null) {
        return;
      }

      NatsRequest natsRequest =
          NatsRequest.create(connection, null, subscription.getSubject(), null, null);

      if (message == null) {
        timeout = new TimeoutException("Timed out waiting for message");
      } else {
        natsRequest = NatsRequest.create(connection, message);
      }

      if (!CONSUMER_INSTRUMENTER.shouldStart(parentContext, natsRequest)) {
        return;
      }

      InstrumenterUtil.startAndEnd(
          CONSUMER_INSTRUMENTER,
          parentContext,
          natsRequest,
          null,
          timeout,
          timer.startTime(),
          timer.now());
    }
  }

  @SuppressWarnings("unused")
  public static class UnsubscribeAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This Subscription subscription) {
      NatsData.removeSubscription(subscription);
    }
  }
}
