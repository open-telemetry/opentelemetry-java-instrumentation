package datadog.trace.agent.tooling.bytebuddy.matcher;

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeTypeDefinitionName;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * An element matcher that matches its argument's {@link TypeDescription.Generic} raw type against
 * the given matcher for a {@link TypeDescription}. As a wildcard does not define an erasure, a
 * runtime exception is thrown when this matcher is applied to a wildcard.
 *
 * <p>Catches and logs exception if it was thrown when getting erasure, returning false.
 *
 * @param <T> The type of the matched entity.
 * @see net.bytebuddy.matcher.ErasureMatcher
 */
@Slf4j
@HashCodeAndEqualsPlugin.Enhance
class SafeErasureMatcher<T extends TypeDefinition> extends ElementMatcher.Junction.AbstractBase<T> {

  /** The matcher to apply to the raw type of the matched element. */
  private final ElementMatcher<? super TypeDescription> matcher;

  /**
   * Creates a new erasure matcher.
   *
   * @param matcher The matcher to apply to the raw type.
   */
  public SafeErasureMatcher(final ElementMatcher<? super TypeDescription> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(final T target) {
    final TypeDescription erasure = safeAsErasure(target);
    if (erasure == null) {
      return false;
    } else {
      // We would like matcher exceptions to propagate
      return matcher.matches(erasure);
    }
  }

  @Override
  public String toString() {
    return "safeErasure(" + matcher + ")";
  }

  static TypeDescription safeAsErasure(final TypeDefinition typeDefinition) {
    try {
      return typeDefinition.asErasure();
    } catch (final Exception e) {
      log.debug(
          "{} trying to get erasure for target {}: {}",
          e.getClass().getSimpleName(),
          safeTypeDefinitionName(typeDefinition),
          e.getMessage());
      return null;
    }
  }
}
