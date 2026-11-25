/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.protocol.AsyncCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LettuceAsyncCommandContextTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void retainsContextOnlyWhileCommandIsPending(boolean done) {
    AsyncCommand<?, ?, ?> command = mock(AsyncCommand.class);
    when(command.isDone()).thenReturn(done);
    Context parent = Context.current();
    Context context = parent.with(ContextKey.named("test"), "value");
    CONTEXT.set(command, context);

    Scope scope = LettuceAsyncCommandInstrumentation.RestoreContextAdvice.onEnter(command);
    try {
      assertThat(Context.current()).isSameAs(context);
    } finally {
      LettuceAsyncCommandInstrumentation.RestoreContextAdvice.onExit(command, scope);
    }

    assertThat(Context.current()).isSameAs(parent);
    assertThat(CONTEXT.get(command)).isSameAs(done ? null : context);
    CONTEXT.set(command, null);
  }

  @Test
  void clearsCompletedContextBeforeClosingScope() {
    AsyncCommand<?, ?, ?> command = mock(AsyncCommand.class);
    when(command.isDone()).thenReturn(true);
    CONTEXT.set(command, Context.root());
    Scope scope = mock(Scope.class);
    IllegalStateException error = new IllegalStateException("scope failure");
    doThrow(error).when(scope).close();

    assertThatThrownBy(
            () -> LettuceAsyncCommandInstrumentation.RestoreContextAdvice.onExit(command, scope))
        .isSameAs(error);

    assertThat(CONTEXT.get(command)).isNull();
    assertThat(LettuceAsyncCommandInstrumentation.RestoreContextAdvice.onEnter(command)).isNull();
  }
}
