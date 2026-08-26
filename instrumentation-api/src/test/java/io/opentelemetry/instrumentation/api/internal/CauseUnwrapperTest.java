/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class CauseUnwrapperTest {

  @Test
  void unwrapsUntilPredicateIsFalse() {
    IllegalArgumentException root = new IllegalArgumentException("root");
    ExecutionException wrapper = new ExecutionException(root);

    assertThat(CauseUnwrapper.unwrapCause(wrapper, error -> error instanceof ExecutionException))
        .isSameAs(root);
  }

  @Test
  void stopsAtNullCause() {
    IllegalArgumentException error = new IllegalArgumentException("no cause");

    assertThat(CauseUnwrapper.unwrapCause(error, unused -> true)).isSameAs(error);
  }

  @Test
  void haltsOnCyclicCauseChain() {
    ExecutionException exception1 = new ExecutionException() {};
    ExecutionException exception2 = new ExecutionException() {};
    exception1.initCause(exception2);
    exception2.initCause(exception1);

    assertThat(CauseUnwrapper.unwrapCause(exception1, error -> error instanceof ExecutionException))
        .isSameAs(exception1);
  }

  @Test
  void supportsCustomAccessor() {
    IllegalStateException underlying = new IllegalStateException("underlying");
    // getCause() is null, so reaching the underlying error is only possible via the accessor
    ExecutionException wrapper = new ExecutionException("wrapper", null);

    Throwable result =
        CauseUnwrapper.unwrap(
            wrapper, error -> error instanceof ExecutionException, unused -> underlying);

    assertThat(result).isSameAs(underlying);
  }

  @Test
  void deepestCauseFindsBottomOfChain() {
    IllegalStateException root = new IllegalStateException("root");
    IllegalArgumentException middle = new IllegalArgumentException("middle", root);
    ExecutionException top = new ExecutionException(middle);

    assertThat(CauseUnwrapper.deepestCause(top)).isSameAs(root);
  }

  @Test
  void deepestCauseHaltsOnCyclicCauseChain() {
    ExecutionException exception1 = new ExecutionException() {};
    ExecutionException exception2 = new ExecutionException() {};
    exception1.initCause(exception2);
    exception2.initCause(exception1);

    // walking exception1 -> exception2 -> exception1 revisits exception1, so unwrapping halts
    // and returns it rather than looping forever
    assertThat(CauseUnwrapper.deepestCause(exception1)).isSameAs(exception1);
  }

  @Test
  void deepestCauseWithNoCauseReturnsSameThrowable() {
    IllegalArgumentException error = new IllegalArgumentException("test");

    assertThat(CauseUnwrapper.deepestCause(error)).isSameAs(error);
  }
}
