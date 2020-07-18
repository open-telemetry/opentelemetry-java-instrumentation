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

package io.opentelemetry.auto.tooling.bytebuddy.matcher;

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.safeTypeDefinitionName;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.SafeErasureMatcher.safeAsErasure;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * An element matcher that matches a super type. This is different from {@link
 * net.bytebuddy.matcher.HasSuperTypeMatcher} in the following way:
 *
 * <ul>
 *   <li>Exceptions are logged
 *   <li>When exception happens the rest of the inheritance subtree is discarded (since ByteBuddy
 *       cannot load/parse type information for it) but search in other subtrees continues
 * </ul>
 *
 * <p>This is useful because this allows us to see when matcher's check is not complete (i.e. part
 * of it fails), at the same time it makes best effort instead of failing quickly (like {@code
 * failSafe(hasSuperType(...))} does) which means the code is more resilient to classpath
 * inconsistencies
 *
 * @param <T> The type of the matched entity.
 * @see net.bytebuddy.matcher.HasSuperTypeMatcher
 */
@Slf4j
class SafeHasSuperTypeMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  /** The matcher to apply to any super type of the matched type. */
  private final ElementMatcher<? super TypeDescription.Generic> matcher;

  private final boolean interfacesOnly;
  /**
   * Creates a new matcher for a super type.
   *
   * @param matcher The matcher to apply to any super type of the matched type.
   */
  public SafeHasSuperTypeMatcher(
      final ElementMatcher<? super TypeDescription.Generic> matcher, final boolean interfacesOnly) {
    this.matcher = matcher;
    this.interfacesOnly = interfacesOnly;
  }

  @Override
  public boolean matches(final T target) {
    Set<TypeDescription> checkedInterfaces = new HashSet<>(8);
    // We do not use foreach loop and iterator interface here because we need to catch exceptions
    // in {@code getSuperClass} calls
    TypeDefinition typeDefinition = target;
    while (typeDefinition != null) {
      if (((!interfacesOnly || typeDefinition.isInterface())
              && matcher.matches(typeDefinition.asGenericType()))
          || hasInterface(typeDefinition, checkedInterfaces)) {
        return true;
      }
      typeDefinition = safeGetSuperClass(typeDefinition);
    }
    return false;
  }

  /**
   * Matches a type's interfaces against the provided matcher.
   *
   * @param typeDefinition The type for which to check all implemented interfaces.
   * @param checkedInterfaces The interfaces that have already been checked.
   * @return {@code true} if any interface matches the supplied matcher.
   */
  private boolean hasInterface(
      final TypeDefinition typeDefinition, final Set<TypeDescription> checkedInterfaces) {
    for (TypeDefinition interfaceType : safeGetInterfaces(typeDefinition)) {
      TypeDescription erasure = safeAsErasure(interfaceType);
      if (erasure != null) {
        if (checkedInterfaces.add(interfaceType.asErasure())
            && (matcher.matches(interfaceType.asGenericType())
                || hasInterface(interfaceType, checkedInterfaces))) {
          return true;
        }
      }
    }
    return false;
  }

  private Iterable<TypeDefinition> safeGetInterfaces(final TypeDefinition typeDefinition) {
    return new SafeInterfaceIterator(typeDefinition);
  }

  static TypeDefinition safeGetSuperClass(final TypeDefinition typeDefinition) {
    try {
      return typeDefinition.getSuperClass();
    } catch (final Exception e) {
      if (log.isDebugEnabled()) {
        log.debug(
            "{} trying to get super class for target {}: {}",
            e.getClass().getSimpleName(),
            safeTypeDefinitionName(typeDefinition),
            e.getMessage());
      }
      return null;
    }
  }

  @Override
  public String toString() {
    return "safeHasSuperType(" + matcher + ")";
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else if (getClass() != other.getClass()) {
      return false;
    } else {
      return matcher.equals(((SafeHasSuperTypeMatcher) other).matcher);
    }
  }

  @Override
  public int hashCode() {
    return 17 * 31 + matcher.hashCode();
  }

  /**
   * TypeDefinition#getInterfaces() produces an iterator which may throw an exception during
   * iteration if an interface is absent from the classpath.
   *
   * <p>The caller MUST call hasNext() before calling next().
   *
   * <p>This wrapper exists to allow getting interfaces even if the lookup on one fails.
   */
  private static class SafeInterfaceIterator
      implements Iterator<TypeDefinition>, Iterable<TypeDefinition> {
    private final TypeDefinition typeDefinition;
    private final Iterator<TypeDescription.Generic> it;
    private TypeDefinition next;

    private SafeInterfaceIterator(TypeDefinition typeDefinition) {
      this.typeDefinition = typeDefinition;
      Iterator<TypeDescription.Generic> it = null;
      try {
        it = typeDefinition.getInterfaces().iterator();
      } catch (Exception e) {
        logException(typeDefinition, e);
      }
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      if (null != it && it.hasNext()) {
        try {
          next = it.next();
          return true;
        } catch (Exception e) {
          logException(typeDefinition, e);
          return false;
        }
      }
      return false;
    }

    @Override
    public TypeDefinition next() {
      return next;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<TypeDefinition> iterator() {
      return this;
    }

    private void logException(TypeDefinition typeDefinition, Exception e) {
      if (log.isDebugEnabled()) {
        log.debug(
            "{} trying to get interfaces for target {}: {}",
            e.getClass().getSimpleName(),
            safeTypeDefinitionName(typeDefinition),
            e.getMessage());
      }
    }
  }
}
