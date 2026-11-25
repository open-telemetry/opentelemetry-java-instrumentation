/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class LettuceReactiveCommandContextTest {

  @Test
  void cancellationClearsOperationState() {
    LettuceReactiveCommandHandler handler =
        new LettuceReactiveCommandHandler(mock(StatefulConnection.class)) {};
    handler.onCommand(new Command<>(CommandType.GET, null));

    handler.onCancel();

    assertThat(handler).extracting("context", "command").containsExactly(null, null);
  }

  @Test
  void noResponseCommandClearsOperationState() {
    LettuceReactiveCommandHandler handler =
        new LettuceReactiveCommandHandler(mock(StatefulConnection.class)) {};

    handler.onCommand(new Command<>(CommandType.SHUTDOWN, null));

    assertThat(handler).extracting("context", "command").containsExactly(null, null);
  }

  @Test
  void clearsOperationStateBeforeFallibleCompletionHook() {
    IllegalStateException error = new IllegalStateException("completion failure");
    LettuceReactiveCommandHandler handler =
        new LettuceReactiveCommandHandler(mock(StatefulConnection.class)) {
          @Override
          protected void onSpanEnding(Context context, boolean cancelled) {
            throw error;
          }
        };
    handler.onCommand(new Command<>(CommandType.GET, null));

    assertThatThrownBy(handler::onCancel).isSameAs(error);

    assertThat(handler).extracting("context", "command").containsExactly(null, null);
  }
}
