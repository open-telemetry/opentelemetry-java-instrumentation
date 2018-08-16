package datadog.trace.agent.tooling.muzzle;

import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import datadog.trace.agent.tooling.AgentInstaller;
import datadog.trace.agent.tooling.Utils;
import datadog.trace.agent.tooling.muzzle.Reference.Mismatch;
import datadog.trace.agent.tooling.muzzle.Reference.Source;
import datadog.trace.bootstrap.WeakMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

/** Matches a set of references against a classloader. */
@Slf4j
public class ReferenceMatcher {
  private final WeakMap<ClassLoader, List<Reference.Mismatch>> mismatchCache = newWeakMap();
  private final Reference[] references;
  private final Set<String> helperClassNames;

  public ReferenceMatcher(final Reference... references) {
    this(new String[0], references);
  }

  public ReferenceMatcher(final String[] helperClassNames, final Reference[] references) {
    this.references = references;
    this.helperClassNames = new HashSet<>(Arrays.asList(helperClassNames));
  }

  public Reference[] getReferences() {
    return references;
  }

  /**
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return true if all references match the classpath of loader
   */
  public boolean matches(final ClassLoader loader) {
    return getMismatchedReferenceSources(loader).size() == 0;
  }

  /**
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return A list of all mismatches between this ReferenceMatcher and loader's classpath.
   */
  public List<Reference.Mismatch> getMismatchedReferenceSources(ClassLoader loader) {
    if (loader == BOOTSTRAP_LOADER) {
      loader = Utils.getBootstrapProxy();
    }
    List<Reference.Mismatch> mismatches = mismatchCache.get(loader);
    if (null == mismatches) {
      synchronized (loader) {
        mismatches = mismatchCache.get(loader);
        if (null == mismatches) {
          mismatches = new ArrayList<>(0);
          for (final Reference reference : references) {
            // Don't reference-check helper classes.
            // They will be injected by the instrumentation's HelperInjector.
            if (!helperClassNames.contains(reference.getClassName())) {
              mismatches.addAll(checkMatch(reference, loader));
            }
          }
          mismatchCache.put(loader, mismatches);
        }
      }
    }
    return mismatches;
  }

  /**
   * Check a reference against a classloader's classpath.
   *
   * @param loader
   * @return A list of mismatched sources. A list of size 0 means the reference matches the class.
   */
  public static List<Reference.Mismatch> checkMatch(Reference reference, ClassLoader loader) {
    final TypePool typePool =
        AgentInstaller.POOL_STRATEGY.typePool(
            AgentInstaller.LOCATION_STRATEGY.classFileLocator(loader), loader);
    final List<Mismatch> mismatches = new ArrayList<>(0);
    try {
      final TypePool.Resolution resolution =
          typePool.describe(Utils.getClassName(reference.getClassName()));
      if (!resolution.isResolved()) {
        return Collections.<Mismatch>singletonList(
            new Mismatch.MissingClass(
                reference.getSources().toArray(new Source[0]), reference.getClassName()));
      }
      return checkMatch(reference, resolution.resolve());
    } catch (Exception e) {
      if (e.getMessage().startsWith("Cannot resolve type description for ")) {
        // bytebuddy throws an illegal state exception with this message if it cannot resolve types
        // TODO: handle missing type resolutions without catching bytebuddy's exceptions
        final String className = e.getMessage().replace("Cannot resolve type description for ", "");
        mismatches.add(
            new Mismatch.MissingClass(reference.getSources().toArray(new Source[0]), className));
      } else {
        // Shouldn't happen. Fail the reference check and add a mismatch for debug logging.
        mismatches.add(new Mismatch.ReferenceCheckError(e, reference, loader));
      }
    }
    return mismatches;
  }

  public static List<Reference.Mismatch> checkMatch(
      Reference reference, TypeDescription typeOnClasspath) {
    final List<Mismatch> mismatches = new ArrayList<>(0);

    for (Reference.Flag flag : reference.getFlags()) {
      if (!flag.matches(typeOnClasspath.getModifiers())) {
        final String desc = reference.getClassName();
        mismatches.add(
            new Mismatch.MissingFlag(
                reference.getSources().toArray(new Source[0]),
                desc,
                flag,
                typeOnClasspath.getModifiers()));
      }
    }

    for (Reference.Field fieldRef : reference.getFields()) {
      FieldDescription.InDefinedShape fieldDescription = findField(fieldRef, typeOnClasspath);
      if (fieldDescription == null) {
        mismatches.add(
            new Reference.Mismatch.MissingField(
                fieldRef.getSources().toArray(new Reference.Source[0]),
                reference.getClassName(),
                fieldRef.getName(),
                fieldRef.getType().getInternalName()));
      } else {
        for (Reference.Flag flag : fieldRef.getFlags()) {
          if (!flag.matches(fieldDescription.getModifiers())) {
            final String desc =
                reference.getClassName()
                    + "#"
                    + fieldRef.getName()
                    + fieldRef.getType().getInternalName();
            mismatches.add(
                new Mismatch.MissingFlag(
                    fieldRef.getSources().toArray(new Source[0]),
                    desc,
                    flag,
                    fieldDescription.getModifiers()));
          }
        }
      }
    }

    for (Reference.Method methodRef : reference.getMethods()) {
      final MethodDescription.InDefinedShape methodDescription =
          findMethod(methodRef, typeOnClasspath);
      if (methodDescription == null) {
        mismatches.add(
            new Reference.Mismatch.MissingMethod(
                methodRef.getSources().toArray(new Reference.Source[0]),
                methodRef.getName(),
                methodRef.getDescriptor()));
      } else {
        for (Reference.Flag flag : methodRef.getFlags()) {
          if (!flag.matches(methodDescription.getModifiers())) {
            final String desc =
                reference.getClassName() + "#" + methodRef.getName() + methodRef.getDescriptor();
            mismatches.add(
                new Mismatch.MissingFlag(
                    methodRef.getSources().toArray(new Source[0]),
                    desc,
                    flag,
                    methodDescription.getModifiers()));
          }
        }
      }
    }

    return mismatches;
  }

  private static FieldDescription.InDefinedShape findField(
      Reference.Field fieldRef, TypeDescription typeOnClasspath) {
    for (FieldDescription.InDefinedShape fieldType : typeOnClasspath.getDeclaredFields()) {
      if (fieldType.getName().equals(fieldRef.getName())
          && fieldType
              .getType()
              .asErasure()
              .getInternalName()
              .equals(fieldRef.getType().getInternalName())) {
        return fieldType;
      }
    }
    if (typeOnClasspath.getSuperClass() != null) {
      FieldDescription.InDefinedShape fieldOnSupertype =
          findField(fieldRef, typeOnClasspath.getSuperClass().asErasure());
      if (fieldOnSupertype != null) {
        return fieldOnSupertype;
      }
    }
    for (TypeDescription.Generic interfaceType : typeOnClasspath.getInterfaces()) {
      FieldDescription.InDefinedShape fieldOnSupertype =
          findField(fieldRef, interfaceType.asErasure());
      if (fieldOnSupertype != null) {
        return fieldOnSupertype;
      }
    }
    return null;
  }

  private static MethodDescription.InDefinedShape findMethod(
      Reference.Method methodRef, TypeDescription typeOnClasspath) {
    for (MethodDescription.InDefinedShape methodDescription :
        typeOnClasspath.getDeclaredMethods()) {
      if (methodDescription.getInternalName().equals(methodRef.getName())
          && methodDescription.getDescriptor().equals(methodRef.getDescriptor())) {
        return methodDescription;
      }
    }
    if (typeOnClasspath.getSuperClass() != null) {
      MethodDescription.InDefinedShape methodOnSupertype =
          findMethod(methodRef, typeOnClasspath.getSuperClass().asErasure());
      if (methodOnSupertype != null) {
        return methodOnSupertype;
      }
    }
    for (TypeDescription.Generic interfaceType : typeOnClasspath.getInterfaces()) {
      MethodDescription.InDefinedShape methodOnSupertype =
          findMethod(methodRef, interfaceType.asErasure());
      if (methodOnSupertype != null) {
        return methodOnSupertype;
      }
    }
    return null;
  }
}
