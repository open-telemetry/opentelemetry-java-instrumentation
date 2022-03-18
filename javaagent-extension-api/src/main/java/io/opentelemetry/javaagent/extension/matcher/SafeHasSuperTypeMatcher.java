/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import static io.opentelemetry.javaagent.extension.matcher.SafeErasureMatcher.safeAsErasure;
import static io.opentelemetry.javaagent.extension.matcher.Utils.safeTypeDefinitionName;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
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
 * @see net.bytebuddy.matcher.HasSuperTypeMatcher
 */
class SafeHasSuperTypeMatcher extends ElementMatcher.Junction.AbstractBase<TypeDescription> {

  private static final Logger logger = Logger.getLogger(SafeHasSuperTypeMatcher.class.getName());

  /** The matcher to apply to any super type of the matched type. */
  private final ElementMatcher<TypeDescription.Generic> matcher;

  private final boolean interfacesOnly;

  /**
   * Creates a new matcher for a super type.
   *
   * @param matcher The matcher to apply to any super type of the matched type.
   */
  public SafeHasSuperTypeMatcher(
      ElementMatcher<TypeDescription.Generic> matcher, boolean interfacesOnly) {
    this.matcher = matcher;
    this.interfacesOnly = interfacesOnly;
  }

  @Override
  public boolean matches(TypeDescription target) {
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
      TypeDefinition typeDefinition, Set<TypeDescription> checkedInterfaces) {
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

  private static Iterable<TypeDefinition> safeGetInterfaces(TypeDefinition typeDefinition) {
    return new SafeInterfaceIterator(typeDefinition);
  }

  static TypeDefinition safeGetSuperClass(TypeDefinition typeDefinition) {
    try {
      return typeDefinition.getSuperClass();
    } catch (Throwable e) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine(
            e.getClass().getSimpleName()
                + " trying to get super class for target "
                + safeTypeDefinitionName(typeDefinition)
                + ": "
                + e.getMessage());
      }
      return null;
    }
  }

  @Override
  public String toString() {
    return "safeHasSuperType(" + matcher + ")";
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SafeHasSuperTypeMatcher)) {
      return false;
    }
    SafeHasSuperTypeMatcher other = (SafeHasSuperTypeMatcher) obj;
    return matcher.equals(other.matcher);
  }

  @Override
  public int hashCode() {
    return matcher.hashCode();
  }

  /**
   * TypeDefinition#getInterfaces() produces an iterator which may throw an exception during
   * iteration if an interface is absent from the classpath.
   *
   * <p>The caller MUST call hasNext() before calling next().
   *
   * <p>This wrapper exists to allow getting interfaces even if the lookup on one fails.
   */
  // Private class, let's save the allocation
  @SuppressWarnings("IterableAndIterator")
  private static class SafeInterfaceIterator
      implements Iterator<TypeDefinition>, Iterable<TypeDefinition> {
    private final TypeDefinition typeDefinition;
    @Nullable private final Iterator<TypeDescription.Generic> it;
    private TypeDefinition next;

    private SafeInterfaceIterator(TypeDefinition typeDefinition) {
      this.typeDefinition = typeDefinition;
      Iterator<TypeDescription.Generic> it = null;
      try {
        it = typeDefinition.getInterfaces().iterator();
      } catch (Throwable e) {
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
        } catch (Throwable e) {
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

    private static void logException(TypeDefinition typeDefinition, Throwable e) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine(
            e.getClass().getSimpleName()
                + " trying to get interfaces for target "
                + safeTypeDefinitionName(typeDefinition)
                + ": "
                + e.getMessage());
      }
    }
  }
}
