package datadog.trace.agent.tooling.bytebuddy.matcher;

import static datadog.trace.agent.tooling.bytebuddy.matcher.SafeHasSuperTypeMatcher.safeGetSuperClass;

import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SafeExtendsClassMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private final ElementMatcher<? super TypeDescription.Generic> matcher;

  public SafeExtendsClassMatcher(final ElementMatcher<? super TypeDescription.Generic> matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(final T target) {
    // We do not use foreach loop and iterator interface here because we need to catch exceptions
    // in {@code getSuperClass} calls
    TypeDefinition typeDefinition = target;
    while (typeDefinition != null) {
      if (matcher.matches(typeDefinition.asGenericType())) {
        return true;
      }
      typeDefinition = safeGetSuperClass(typeDefinition);
    }
    return false;
  }
}
