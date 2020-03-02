package datadog.trace.agent.tooling.bytebuddy.matcher;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * A fail-safe matcher catches exceptions that are thrown by a delegate matcher and returns an
 * alternative value.
 *
 * <p>Logs exception if it was thrown.
 *
 * @param <T> The type of the matched entity.
 * @see net.bytebuddy.matcher.FailSafeMatcher
 */
@Slf4j
class LoggingFailSafeMatcher<T> extends ElementMatcher.Junction.AbstractBase<T> {

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
  public boolean equals(final Object var1) {
    if (this == var1) {
      return true;
    } else if (var1 == null) {
      return false;
    } else if (getClass() != var1.getClass()) {
      return false;
    } else if (fallback != ((LoggingFailSafeMatcher) var1).fallback) {
      return false;
    } else {
      return matcher.equals(((LoggingFailSafeMatcher) var1).matcher);
    }
  }

  @Override
  public int hashCode() {
    return (17 * 31 + matcher.hashCode()) * 31 + (fallback ? 1231 : 1237);
  }
}
