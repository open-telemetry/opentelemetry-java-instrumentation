/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * A fail-safe matcher catches exceptions that are thrown by a delegate matcher and returns {@code
 * false}.
 *
 * <p>Logs exception if it was thrown.
 *
 * @param <T> The type of the matched entity.
 * @see net.bytebuddy.matcher.FailSafeMatcher
 */
public class LoggingFailSafeMatcher<T> extends ElementMatcher.Junction.AbstractBase<T> {

  private static final Logger logger = Logger.getLogger(LoggingFailSafeMatcher.class.getName());

  /** The delegate matcher that might throw an exception. */
  private final ElementMatcher<? super T> matcher;

  /** The text description to log if exception happens. */
  private final String description;

  /**
   * Creates a new fail-safe element matcher.
   *
   * @param matcher The delegate matcher that might throw an exception.
   * @param description Descriptive string to log along with exception.
   */
  public LoggingFailSafeMatcher(ElementMatcher<? super T> matcher, String description) {
    this.matcher = matcher;
    this.description = description;
  }

  @Override
  public boolean matches(T target) {
    try {
      return matcher.matches(target);
    } catch (Throwable e) {
      logger.log(Level.FINE, description, e);
      return false;
    }
  }

  @Override
  public String toString() {
    return "failSafe(try(" + matcher + ") or false)";
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LoggingFailSafeMatcher)) {
      return false;
    }
    LoggingFailSafeMatcher<?> other = (LoggingFailSafeMatcher<?>) obj;
    return matcher.equals(other.matcher);
  }

  @Override
  public int hashCode() {
    return matcher.hashCode();
  }
}
