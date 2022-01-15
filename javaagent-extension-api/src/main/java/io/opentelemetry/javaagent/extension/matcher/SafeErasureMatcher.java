/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import static io.opentelemetry.javaagent.extension.matcher.Utils.safeTypeDefinitionName;

import javax.annotation.Nullable;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class SafeErasureMatcher<T extends TypeDefinition> extends ElementMatcher.Junction.AbstractBase<T> {

  private static final Logger logger = LoggerFactory.getLogger(SafeErasureMatcher.class);

  /** The matcher to apply to the raw type of the matched element. */
  private final ElementMatcher<TypeDescription> matcher;

  /**
   * Creates a new erasure matcher.
   *
   * @param matcher The matcher to apply to the raw type.
   */
  public SafeErasureMatcher(ElementMatcher<TypeDescription> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(T target) {
    TypeDescription erasure = safeAsErasure(target);
    if (erasure == null) {
      return false;
    } else {
      // We would like matcher exceptions to propagate
      return matcher.matches(erasure);
    }
  }

  static TypeDescription safeAsErasure(TypeDefinition typeDefinition) {
    try {
      return typeDefinition.asErasure();
    } catch (Throwable e) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "{} trying to get erasure for target {}: {}",
            e.getClass().getSimpleName(),
            safeTypeDefinitionName(typeDefinition),
            e.getMessage());
      }
      return null;
    }
  }

  @Override
  public String toString() {
    return "safeErasure(" + matcher + ")";
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SafeErasureMatcher)) {
      return false;
    }
    SafeErasureMatcher<?> other = (SafeErasureMatcher<?>) obj;
    return matcher.equals(other.matcher);
  }

  @Override
  public int hashCode() {
    return matcher.hashCode();
  }
}
