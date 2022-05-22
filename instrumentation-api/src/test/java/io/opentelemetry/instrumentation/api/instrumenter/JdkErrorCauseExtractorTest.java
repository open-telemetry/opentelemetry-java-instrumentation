/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdkErrorCauseExtractorTest {

  @ParameterizedTest
  @ValueSource(
      classes = {
        ExecutionException.class,
        CompletionException.class,
        InvocationTargetException.class,
        UndeclaredThrowableException.class
      })
  void unwraps(Class<? extends Exception> exceptionClass) throws Exception {
    Exception exception =
        exceptionClass
            .getConstructor(Throwable.class)
            .newInstance(new IllegalArgumentException("test"));

    assertThat(ErrorCauseExtractor.jdk().extract(exception))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("test");
  }

  @Test
  void multipleUnwraps() {
    assertThat(
            ErrorCauseExtractor.jdk()
                .extract(
                    new ExecutionException(
                        new UndeclaredThrowableException(new IllegalArgumentException("test")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("test");
  }

  @Test
  void notWrapped() {
    assertThat(ErrorCauseExtractor.jdk().extract(new IllegalArgumentException("test")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("test");
    assertThat(
            ErrorCauseExtractor.jdk()
                .extract(new IllegalArgumentException("test", new IllegalStateException("state"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("test");
  }
}
