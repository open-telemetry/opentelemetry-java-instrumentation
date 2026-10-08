/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A helper for safely walking a chain of {@link Throwable} causes.
 *
 * <p>Instrumentation frequently needs to unwrap wrapper exceptions (e.g. {@link
 * java.util.concurrent.ExecutionException}, framework-specific wrapper types) to find the
 * underlying error. A naive loop or recursive method that follows {@link Throwable#getCause()} (or
 * a similar library-specific accessor) until some stop condition is met can loop forever, or
 * overflow the stack if written recursively, when a cause chain contains a cycle - which can happen
 * with adversarial or buggy exceptions since nothing prevents {@link
 * Throwable#initCause(Throwable)} from being used to create one. The helpers in this class detect
 * such cycles using identity comparisons and stop unwrapping instead of looping.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class CauseUnwrapper {

  /**
   * Repeatedly replaces {@code error} with {@code nextCause.apply(error)} while {@code
   * shouldUnwrap.test(error)} returns {@code true}, and returns the last value reached.
   *
   * <p>Unwrapping halts, whichever happens first: when {@code shouldUnwrap} returns {@code false},
   * when {@code nextCause} returns {@code null}, or when a throwable that has already been visited
   * (compared by identity) would be visited again.
   */
  public static Throwable unwrap(
      Throwable error,
      Predicate<Throwable> shouldUnwrap,
      Function<Throwable, Throwable> nextCause) {
    Set<Throwable> visited = null;
    Throwable current = error;
    while (shouldUnwrap.test(current)) {
      Throwable next = nextCause.apply(current);
      if (next == null) {
        break;
      }
      if (visited == null) {
        visited = Collections.newSetFromMap(new IdentityHashMap<>());
      }
      if (!visited.add(current)) {
        break;
      }
      current = next;
    }
    return current;
  }

  /**
   * Equivalent to {@link #unwrap(Throwable, Predicate, Function)}, using {@link
   * Throwable#getCause()} to advance to the next cause.
   */
  public static Throwable unwrap(Throwable error, Predicate<Throwable> shouldUnwrap) {
    return unwrap(error, shouldUnwrap, Throwable::getCause);
  }

  /**
   * Follows {@link Throwable#getCause()} to the root cause in the chain, halting safely if the
   * chain contains a cycle.
   */
  public static Throwable rootCause(Throwable error) {
    return unwrap(error, unused -> true);
  }

  private CauseUnwrapper() {}
}
