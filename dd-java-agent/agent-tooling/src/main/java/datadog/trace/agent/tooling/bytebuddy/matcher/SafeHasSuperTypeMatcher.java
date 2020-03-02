package datadog.trace.agent.tooling.bytebuddy.matcher;

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeTypeDefinitionName;
import static datadog.trace.agent.tooling.bytebuddy.matcher.SafeErasureMatcher.safeAsErasure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.build.HashCodeAndEqualsPlugin;
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
@HashCodeAndEqualsPlugin.Enhance
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
    final Set<TypeDescription> checkedInterfaces = new HashSet<>();
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
    for (final TypeDefinition interfaceType : safeGetInterfaces(typeDefinition)) {
      final TypeDescription erasure = safeAsErasure(interfaceType);
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

  /**
   * TypeDefinition#getInterfaces() produces an interator which may throw an exception during
   * iteration if an interface is absent from the classpath.
   *
   * <p>This method exists to allow getting interfaces even if the lookup on one fails.
   */
  private List<TypeDefinition> safeGetInterfaces(final TypeDefinition typeDefinition) {
    final List<TypeDefinition> interfaceTypes = new ArrayList<>();
    try {
      final Iterator<TypeDescription.Generic> interfaceIter =
          typeDefinition.getInterfaces().iterator();
      while (interfaceIter.hasNext()) {
        interfaceTypes.add(interfaceIter.next());
      }
    } catch (final Exception e) {
      log.debug(
          "{} trying to get interfaces for target {}: {}",
          e.getClass().getSimpleName(),
          safeTypeDefinitionName(typeDefinition),
          e.getMessage());
    }
    return interfaceTypes;
  }

  static TypeDefinition safeGetSuperClass(final TypeDefinition typeDefinition) {
    try {
      return typeDefinition.getSuperClass();
    } catch (final Exception e) {
      log.debug(
          "{} trying to get super class for target {}: {}",
          e.getClass().getSimpleName(),
          safeTypeDefinitionName(typeDefinition),
          e.getMessage());
      return null;
    }
  }

  @Override
  public String toString() {
    return "safeHasSuperType(" + matcher + ")";
  }
}
