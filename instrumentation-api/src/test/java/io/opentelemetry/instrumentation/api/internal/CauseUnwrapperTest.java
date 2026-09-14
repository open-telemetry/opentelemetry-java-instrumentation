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

    assertThat(CauseUnwrapper.unwrap(wrapper, error -> error instanceof ExecutionException))
        .isSameAs(root);
  }

  @Test
  void stopsAtNullCause() {
    IllegalArgumentException error = new IllegalArgumentException("no cause");

    assertThat(CauseUnwrapper.unwrap(error, unused -> true)).isSameAs(error);
  }

  @Test
  void haltsOnCyclicCauseChain() {
    ExecutionException exception1 = new ExecutionException() {};
    ExecutionException exception2 = new ExecutionException() {};
    exception1.initCause(exception2);
    exception2.initCause(exception1);

    assertThat(CauseUnwrapper.unwrap(exception1, error -> error instanceof ExecutionException))
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
  void rootCauseFindsBottomOfChain() {
    IllegalStateException root = new IllegalStateException("root");
    IllegalArgumentException middle = new IllegalArgumentException("middle", root);
    ExecutionException top = new ExecutionException(middle);

    assertThat(CauseUnwrapper.rootCause(top)).isSameAs(root);
  }

  @Test
  void rootCauseHaltsOnCyclicCauseChain() {
    ExecutionException exception1 = new ExecutionException() {};
    ExecutionException exception2 = new ExecutionException() {};
    exception1.initCause(exception2);
    exception2.initCause(exception1);

    // walking exception1 -> exception2 -> exception1 revisits exception1, so unwrapping halts
    // and returns it rather than looping forever
    assertThat(CauseUnwrapper.rootCause(exception1)).isSameAs(exception1);
  }

  @Test
  void rootCauseWithNoCauseReturnsSameThrowable() {
    IllegalArgumentException error = new IllegalArgumentException("test");

    assertThat(CauseUnwrapper.rootCause(error)).isSameAs(error);
  }
}
