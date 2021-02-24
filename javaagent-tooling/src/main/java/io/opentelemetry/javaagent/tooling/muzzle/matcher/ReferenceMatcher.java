/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.matcher;

import static io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate.isInstrumentationClass;
import static java.util.Collections.emptyList;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import io.opentelemetry.javaagent.bootstrap.WeakCache;
import io.opentelemetry.javaagent.tooling.AgentTooling;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Source;
import io.opentelemetry.javaagent.tooling.muzzle.matcher.HelperReferenceWrapper.Factory;
import io.opentelemetry.javaagent.tooling.muzzle.matcher.HelperReferenceWrapper.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

/** Matches a set of references against a classloader. */
public final class ReferenceMatcher {

  private final WeakCache<ClassLoader, Boolean> mismatchCache = AgentTooling.newWeakCache();
  private final Map<String, Reference> references;
  private final Set<String> helperClassNames;

  public ReferenceMatcher(Reference... references) {
    this(emptyList(), references);
  }

  public ReferenceMatcher(List<String> helperClassNames, Reference[] references) {
    this.references = new HashMap<>(references.length);
    for (Reference reference : references) {
      this.references.put(reference.getClassName(), reference);
    }
    this.helperClassNames = new HashSet<>(helperClassNames);
  }

  Collection<Reference> getReferences() {
    return references.values();
  }

  /**
   * Matcher used by ByteBuddy. Fails fast and only caches empty results, or complete results
   *
   * @param userClassLoader Classloader to validate against (or null for bootstrap)
   * @return true if all references match the classpath of loader
   */
  public boolean matches(ClassLoader userClassLoader) {
    if (userClassLoader == BOOTSTRAP_LOADER) {
      userClassLoader = Utils.getBootstrapProxy();
    }
    final ClassLoader cl = userClassLoader;
    return mismatchCache.getIfPresentOrCompute(userClassLoader, () -> doesMatch(cl));
  }

  private boolean doesMatch(ClassLoader loader) {
    for (Reference reference : references.values()) {
      if (!checkMatch(reference, loader).isEmpty()) {
        return false;
      }
    }

    return true;
  }

  /**
   * Loads the full list of mismatches. Used in debug contexts only
   *
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return A list of all mismatches between this ReferenceMatcher and loader's classpath.
   */
  public List<Mismatch> getMismatchedReferenceSources(ClassLoader loader) {
    if (loader == BOOTSTRAP_LOADER) {
      loader = Utils.getBootstrapProxy();
    }

    List<Mismatch> mismatches = emptyList();

    for (Reference reference : references.values()) {
      mismatches = lazyAddAll(mismatches, checkMatch(reference, loader));
    }

    return mismatches;
  }

  /**
   * Check a reference against a classloader's classpath.
   *
   * @return A list of mismatched sources. A list of size 0 means the reference matches the class.
   */
  private List<Mismatch> checkMatch(Reference reference, ClassLoader loader) {
    TypePool typePool =
        AgentTooling.poolStrategy()
            .typePool(AgentTooling.locationStrategy().classFileLocator(loader), loader);
    try {
      if (isInstrumentationClass(reference.getClassName())) {
        // make sure helper class is registered
        if (!helperClassNames.contains(reference.getClassName())) {
          return Collections.singletonList(
              new Mismatch.MissingClass(
                  reference.getSources().toArray(new Source[0]), reference.getClassName()));
        }
        // helper classes get their own check: whether they implement all abstract methods
        return checkHelperClassMatch(reference, typePool);
      } else if (helperClassNames.contains(reference.getClassName())) {
        // skip muzzle check for those helper classes that are not in instrumentation packages; e.g.
        // some instrumentations inject guava types as helper classes
        return emptyList();
      } else {
        TypePool.Resolution resolution = typePool.describe(reference.getClassName());
        if (!resolution.isResolved()) {
          return Collections.singletonList(
              new Mismatch.MissingClass(
                  reference.getSources().toArray(new Source[0]), reference.getClassName()));
        }
        return checkThirdPartyTypeMatch(reference, resolution.resolve());
      }
    } catch (Exception e) {
      if (e.getMessage().startsWith("Cannot resolve type description for ")) {
        // bytebuddy throws an illegal state exception with this message if it cannot resolve types
        // TODO: handle missing type resolutions without catching bytebuddy's exceptions
        String className = e.getMessage().replace("Cannot resolve type description for ", "");
        return Collections.singletonList(
            new Mismatch.MissingClass(reference.getSources().toArray(new Source[0]), className));
      } else {
        // Shouldn't happen. Fail the reference check and add a mismatch for debug logging.
        return Collections.singletonList(new Mismatch.ReferenceCheckError(e, reference, loader));
      }
    }
  }

  // for helper classes we make sure that all abstract methods from super classes and interfaces are
  // implemented
  private List<Mismatch> checkHelperClassMatch(Reference helperClass, TypePool typePool) {
    List<Mismatch> mismatches = emptyList();

    HelperReferenceWrapper helperWrapper = new Factory(typePool, references).create(helperClass);

    if (!helperWrapper.hasSuperTypes() || helperWrapper.isAbstract()) {
      return mismatches;
    }

    // treat the helper type as a bag of methods: collect all methods defined in the helper class,
    // all superclasses and interfaces and check if all abstract methods are implemented somewhere
    Set<HelperReferenceWrapper.Method> abstractMethods = new HashSet<>();
    Set<HelperReferenceWrapper.Method> plainMethods = new HashSet<>();
    collectMethodsFromTypeHierarchy(helperWrapper, abstractMethods, plainMethods);

    abstractMethods.removeAll(plainMethods);
    for (HelperReferenceWrapper.Method unimplementedMethod : abstractMethods) {
      mismatches =
          lazyAdd(
              mismatches,
              new Mismatch.MissingMethod(
                  helperClass.getSources().toArray(new Reference.Source[0]),
                  unimplementedMethod.getDeclaringClass(),
                  unimplementedMethod.getName(),
                  unimplementedMethod.getDescriptor()));
    }

    return mismatches;
  }

  private static void collectMethodsFromTypeHierarchy(
      HelperReferenceWrapper type, Set<Method> abstractMethods, Set<Method> plainMethods) {

    type.getMethods()
        .forEach(method -> (method.isAbstract() ? abstractMethods : plainMethods).add(method));

    type.getSuperTypes()
        .forEach(
            superType -> collectMethodsFromTypeHierarchy(superType, abstractMethods, plainMethods));
  }

  private static List<Mismatch> checkThirdPartyTypeMatch(
      Reference reference, TypeDescription typeOnClasspath) {
    List<Mismatch> mismatches = Collections.emptyList();

    for (Reference.Flag flag : reference.getFlags()) {
      if (!flag.matches(typeOnClasspath.getActualModifiers(false))) {
        String desc = reference.getClassName();
        mismatches =
            lazyAdd(
                mismatches,
                new Mismatch.MissingFlag(
                    reference.getSources().toArray(new Source[0]),
                    desc,
                    flag,
                    typeOnClasspath.getActualModifiers(false)));
      }
    }

    for (Reference.Field fieldRef : reference.getFields()) {
      FieldDescription.InDefinedShape fieldDescription = findField(fieldRef, typeOnClasspath);
      if (fieldDescription == null) {
        mismatches =
            lazyAdd(
                mismatches,
                new Mismatch.MissingField(
                    fieldRef.getSources().toArray(new Reference.Source[0]),
                    reference.getClassName(),
                    fieldRef.getName(),
                    fieldRef.getType().getInternalName()));
      } else {
        for (Reference.Flag flag : fieldRef.getFlags()) {
          if (!flag.matches(fieldDescription.getModifiers())) {
            String desc =
                reference.getClassName()
                    + "#"
                    + fieldRef.getName()
                    + fieldRef.getType().getInternalName();
            mismatches =
                lazyAdd(
                    mismatches,
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
      MethodDescription.InDefinedShape methodDescription = findMethod(methodRef, typeOnClasspath);
      if (methodDescription == null) {
        mismatches =
            lazyAdd(
                mismatches,
                new Mismatch.MissingMethod(
                    methodRef.getSources().toArray(new Reference.Source[0]),
                    reference.getClassName(),
                    methodRef.getName(),
                    methodRef.getDescriptor()));
      } else {
        for (Reference.Flag flag : methodRef.getFlags()) {
          if (!flag.matches(methodDescription.getModifiers())) {
            String desc =
                reference.getClassName() + "#" + methodRef.getName() + methodRef.getDescriptor();
            mismatches =
                lazyAdd(
                    mismatches,
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

  private static boolean matchesPrimitive(String longName, String shortName) {
    // The two meta type systems in use here differ in their treatment of primitive type names....
    return shortName.equals("I") && longName.equals(int.class.getName())
        || shortName.equals("C") && longName.equals(char.class.getName())
        || shortName.equals("Z") && longName.equals(boolean.class.getName())
        || shortName.equals("J") && longName.equals(long.class.getName())
        || shortName.equals("S") && longName.equals(short.class.getName())
        || shortName.equals("F") && longName.equals(float.class.getName())
        || shortName.equals("D") && longName.equals(double.class.getName())
        || shortName.equals("B") && longName.equals(byte.class.getName());
  }

  private static FieldDescription.InDefinedShape findField(
      Reference.Field fieldRef, TypeDescription typeOnClasspath) {
    for (FieldDescription.InDefinedShape fieldType : typeOnClasspath.getDeclaredFields()) {
      if (fieldType.getName().equals(fieldRef.getName())
          && ((fieldType
                  .getType()
                  .asErasure()
                  .getInternalName()
                  .equals(fieldRef.getType().getInternalName()))
              || (fieldType.getType().asErasure().isPrimitive()
                  && matchesPrimitive(
                      fieldType.getType().asErasure().getInternalName(),
                      fieldRef.getType().getInternalName())))) {
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

  // optimization to avoid ArrayList allocation in the common case when there are no mismatches
  private static List<Mismatch> lazyAdd(List<Mismatch> mismatches, Mismatch mismatch) {
    List<Mismatch> result = mismatches.isEmpty() ? new ArrayList<>() : mismatches;
    result.add(mismatch);
    return result;
  }

  // optimization to avoid ArrayList allocation in the common case when there are no mismatches
  private static List<Mismatch> lazyAddAll(List<Mismatch> mismatches, List<Mismatch> toAdd) {
    if (!toAdd.isEmpty()) {
      List<Mismatch> result = mismatches.isEmpty() ? new ArrayList<>() : mismatches;
      result.addAll(toAdd);
      return result;
    }
    return mismatches;
  }
}
