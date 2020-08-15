/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A fail-safe matcher catches exceptions that are thrown by a delegate matcher and returns an
 * alternative value.
 *
 * <p>Logs exception if it was thrown.
 *
 * @param <T> The type of the matched entity.
 * @see net.bytebuddy.matcher.FailSafeMatcher
 */
class LoggingFailSafeMatcher<T> extends ElementMatcher.Junction.AbstractBase<T> {

  private static final Logger log = LoggerFactory.getLogger(LoggingFailSafeMatcher.class);

  /** The delegate matcher that might throw an exception. */
  private final ElementMatcher<? super T> matcher;

  /** The fallback value in case of an exception. */
  private final boolean fallback;

  /** The text description to log if exception happens. */
  private final String description;

  /**
   * Creates a new fail-safe element matcher.
   *
   * @param matcher The delegate matcher that might throw an exception.
   * @param fallback The fallback value in case of an exception.
   * @param description Descriptive string to log along with exception.
   */
  public LoggingFailSafeMatcher(
      final ElementMatcher<? super T> matcher, final boolean fallback, final String description) {
    this.matcher = matcher;
    this.fallback = fallback;
    this.description = description;
  }

  @Override
  public boolean matches(final T target) {
    try {
      return matcher.matches(target);
    } catch (final Exception e) {
      log.debug(description, e);
      return fallback;
    }
  }

  @Override
  public String toString() {
    return "failSafe(try(" + matcher + ") or " + fallback + ")";
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else if (getClass() != other.getClass()) {
      return false;
    } else if (fallback != ((LoggingFailSafeMatcher) other).fallback) {
      return false;
    } else {
      return matcher.equals(((LoggingFailSafeMatcher) other).matcher);
    }
  }

  @Override
  public int hashCode() {
    return (17 * 31 + matcher.hashCode()) * 31 + (fallback ? 1231 : 1237);
  }
}
