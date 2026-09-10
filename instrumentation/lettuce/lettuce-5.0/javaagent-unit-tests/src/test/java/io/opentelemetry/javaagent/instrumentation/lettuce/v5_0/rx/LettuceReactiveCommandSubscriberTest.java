/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.protocol.RedisCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

class LettuceReactiveCommandSubscriberTest {

  @Test
  void cancellationStillCancelsUpstreamWhenHandlerFails() {
    CoreSubscriber<?> actual = mock(CoreSubscriber.class);
    when(actual.currentContext()).thenReturn(Context.empty());
    Subscription upstream = mock(Subscription.class);
    LettuceReactiveCommandHandler handler =
        new LettuceReactiveCommandHandler() {
          @Override
          public void onCommand(RedisCommand<?, ?, ?> command) {}

          @Override
          public void onCancel() {
            throw new IllegalStateException("test");
          }
        };

    LettuceReactiveCommandSubscriber.withCancellation(actual, handler).onSubscribe(upstream);

    ArgumentCaptor<Subscription> subscription = ArgumentCaptor.forClass(Subscription.class);
    verify(actual).onSubscribe(subscription.capture());

    assertThatThrownBy(subscription.getValue()::cancel)
        .hasRootCauseInstanceOf(IllegalStateException.class);
    verify(upstream).cancel();
  }
}
