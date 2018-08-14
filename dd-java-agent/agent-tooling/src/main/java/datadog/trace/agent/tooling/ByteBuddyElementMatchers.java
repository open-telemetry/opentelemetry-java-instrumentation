package datadog.trace.agent.tooling;

import static net.bytebuddy.matcher.ElementMatchers.erasure;

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
import net.bytebuddy.matcher.ElementMatchers;

/**
 * This class provides some custom ByteBuddy element matchers to use when applying instrumentation
 */
@Slf4j
public class ByteBuddyElementMatchers {

  /**
   * Matches any type description that declares a super type that matches the provided matcher.
   * Exceptions during matching process are logged and ignored.
   *
   * @param matcher The type to be checked for being a super type of the matched type.
   * @param <T> The type of the matched object.
   * @return A matcher that matches any type description that declares a super type that matches the
   *     provided matcher.
   * @see ElementMatchers#hasSuperType(net.bytebuddy.matcher.ElementMatcher)
   */
  public static <T extends TypeDescription> ElementMatcher.Junction<T> safeHasSuperType(
      final ElementMatcher<? super TypeDescription> matcher) {
    return safeHasGenericSuperType(erasure(matcher));
  }

  /**
   * Matches any type description that declares a super type that matches the provided matcher.
   * Exceptions during matching process are logged and ignored.
   *
   * @param matcher The type to be checked for being a super type of the matched type.
   * @param <T> The type of the matched object.
   * @return A matcher that matches any type description that declares a super type that matches the
   *     provided matcher.
   * @see ElementMatchers#hasGenericSuperType(net.bytebuddy.matcher.ElementMatcher)
   */
  public static <T extends TypeDescription> ElementMatcher.Junction<T> safeHasGenericSuperType(
      final ElementMatcher<? super TypeDescription.Generic> matcher) {
    return new SafeHasSuperTypeMatcher<>(matcher);
  }

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
  @HashCodeAndEqualsPlugin.Enhance
  public static class SafeHasSuperTypeMatcher<T extends TypeDescription>
      extends ElementMatcher.Junction.AbstractBase<T> {

    /** The matcher to apply to any super type of the matched type. */
    private final ElementMatcher<? super TypeDescription.Generic> matcher;

    /**
     * Creates a new matcher for a super type.
     *
     * @param matcher The matcher to apply to any super type of the matched type.
     */
    public SafeHasSuperTypeMatcher(final ElementMatcher<? super TypeDescription.Generic> matcher) {
      this.matcher = matcher;
    }

    @Override
    public boolean matches(final T target) {
      final Set<TypeDescription> checkedInterfaces = new HashSet<>();
      // We do not use foreach loop and iterator interface here because we need to catch exceptions
      // in {@code getSuperClass} calls
      TypeDefinition typeDefinition = target;
      while (typeDefinition != null) {
        if (matcher.matches(typeDefinition.asGenericType())
            || hasInterface(typeDefinition, checkedInterfaces)) {
          return true;
        }
        typeDefinition = safeGetSuperClass(typeDefinition);
      }
      return false;
    }

    private TypeDefinition safeGetSuperClass(final TypeDefinition typeDefinition) {
      try {
        return typeDefinition.getSuperClass();
      } catch (final Exception e) {
        log.debug("Exception trying to get next type definition:", e);
        return null;
      }
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
        if (checkedInterfaces.add(interfaceType.asErasure())
            && (matcher.matches(interfaceType.asGenericType())
                || hasInterface(interfaceType, checkedInterfaces))) {
          return true;
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
        log.debug("Exception trying to get interfaces:", e);
      }
      return interfaceTypes;
    }

    @Override
    public String toString() {
      return "safeHasSuperType(" + matcher + ")";
    }
  }
}
