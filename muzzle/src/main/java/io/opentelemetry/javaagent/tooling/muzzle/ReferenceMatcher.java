/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.FieldRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.MethodRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.Type;

/** Matches a set of references against a classloader. */
public final class ReferenceMatcher {

  private final Cache<ClassLoader, Boolean> mismatchCache =
      Cache.newBuilder().setWeakKeys().build();
  private final Map<String, ClassRef> references;
  private final Set<String> helperClassNames;
  private final HelperClassPredicate helperClassPredicate;

  public static ReferenceMatcher of(InstrumentationModule instrumentationModule) {
    return new ReferenceMatcher(
        InstrumentationModuleMuzzle.getHelperClassNames(instrumentationModule),
        InstrumentationModuleMuzzle.getMuzzleReferences(instrumentationModule),
        instrumentationModule::isHelperClass);
  }

  ReferenceMatcher(
      List<String> helperClassNames,
      Map<String, ClassRef> references,
      Predicate<String> libraryInstrumentationPredicate) {
    this.references = references;
    this.helperClassNames = new HashSet<>(helperClassNames);
    this.helperClassPredicate = new HelperClassPredicate(libraryInstrumentationPredicate);
  }

  /**
   * Matcher used by ByteBuddy. Fails fast and only caches empty results, or complete results
   *
   * @param userClassLoader Classloader to validate against (cannot be {@code null}, must pass
   *     "bootstrap proxy" instead of bootstrap class loader)
   * @return true if all references match the classpath of loader
   */
  public boolean matches(ClassLoader userClassLoader) {
    return mismatchCache.computeIfAbsent(userClassLoader, this::doesMatch);
  }

  // loader cannot be null, must pass "bootstrap proxy" instead of bootstrap class loader
  private boolean doesMatch(ClassLoader loader) {
    TypePool typePool = createTypePool(loader);
    for (ClassRef reference : references.values()) {
      if (!checkMatch(reference, typePool, loader).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Loads the full list of mismatches. Used in debug contexts only
   *
   * @param loader Classloader to validate against (cannot be {@code null}, must pass "bootstrap *
   *     proxy" instead of bootstrap class loader)
   * @return A list of all mismatches between this ReferenceMatcher and loader's classpath.
   */
  public List<Mismatch> getMismatchedReferenceSources(ClassLoader loader) {
    TypePool typePool = createTypePool(loader);

    List<Mismatch> mismatches = emptyList();

    for (ClassRef reference : references.values()) {
      mismatches = addAll(mismatches, checkMatch(reference, typePool, loader));
    }

    return mismatches;
  }

  // loader cannot be null, must pass "bootstrap proxy" instead of bootstrap class loader
  private static TypePool createTypePool(ClassLoader loader) {
    // ok to use locationStrategy() without fallback bootstrap proxy here since loader is non-null
    return AgentTooling.poolStrategy()
        .typePool(AgentTooling.locationStrategy().classFileLocator(loader), loader);
  }

  /**
   * Check a reference against a classloader's classpath.
   *
   * @return A list of mismatched sources. A list of size 0 means the reference matches the class.
   */
  private List<Mismatch> checkMatch(ClassRef reference, TypePool typePool, ClassLoader loader) {
    try {
      if (helperClassPredicate.isHelperClass(reference.getClassName())) {
        // make sure helper class is registered
        if (!helperClassNames.contains(reference.getClassName())) {
          return singletonList(new Mismatch.MissingClass(reference));
        }
        // helper classes get their own check: whether they implement all abstract methods
        return checkHelperClassMatch(reference, typePool);
      } else {
        TypePool.Resolution resolution = typePool.describe(reference.getClassName());
        if (!resolution.isResolved()) {
          return singletonList(new Mismatch.MissingClass(reference));
        }
        return checkThirdPartyTypeMatch(reference, resolution.resolve());
      }
    } catch (RuntimeException e) {
      if (e.getMessage().startsWith("Cannot resolve type description for ")) {
        // bytebuddy throws an illegal state exception with this message if it cannot resolve types
        // TODO: handle missing type resolutions without catching bytebuddy's exceptions
        String className = e.getMessage().replace("Cannot resolve type description for ", "");
        return singletonList(new Mismatch.MissingClass(reference, className));
      } else {
        // Shouldn't happen. Fail the reference check and add a mismatch for debug logging.
        return singletonList(new Mismatch.ReferenceCheckError(e, reference, loader));
      }
    }
  }

  // for helper classes we make sure that all abstract methods from super classes and interfaces are
  // implemented and that all accessed fields are defined somewhere in the type hierarchy
  private List<Mismatch> checkHelperClassMatch(ClassRef helperClass, TypePool typePool) {
    List<Mismatch> mismatches = emptyList();

    HelperReferenceWrapper helperWrapper =
        new HelperReferenceWrapper.Factory(typePool, references).create(helperClass);

    Set<HelperReferenceWrapper.Field> undeclaredFields =
        helperClass.getFields().stream()
            .filter(f -> !f.isDeclared())
            .map(f -> new HelperReferenceWrapper.Field(f.getName(), f.getDescriptor()))
            .collect(Collectors.toSet());

    // if there are any fields in this helper class that's not declared here, check the type
    // hierarchy
    if (!undeclaredFields.isEmpty()) {
      Set<HelperReferenceWrapper.Field> superClassFields = new HashSet<>();
      collectFieldsFromTypeHierarchy(helperWrapper, superClassFields);

      undeclaredFields.removeAll(superClassFields);
      for (HelperReferenceWrapper.Field missingField : undeclaredFields) {
        mismatches = add(mismatches, new Mismatch.MissingField(helperClass, missingField));
      }
    }

    // skip abstract method check if this type does not have super type or is abstract
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
      mismatches = add(mismatches, new Mismatch.MissingMethod(helperClass, unimplementedMethod));
    }

    return mismatches;
  }

  private static void collectFieldsFromTypeHierarchy(
      HelperReferenceWrapper type, Set<HelperReferenceWrapper.Field> fields) {

    type.getFields().forEach(fields::add);
    type.getSuperTypes().forEach(superType -> collectFieldsFromTypeHierarchy(superType, fields));
  }

  private static void collectMethodsFromTypeHierarchy(
      HelperReferenceWrapper type,
      Set<HelperReferenceWrapper.Method> abstractMethods,
      Set<HelperReferenceWrapper.Method> plainMethods) {

    type.getMethods()
        .forEach(method -> (method.isAbstract() ? abstractMethods : plainMethods).add(method));

    type.getSuperTypes()
        .forEach(
            superType -> collectMethodsFromTypeHierarchy(superType, abstractMethods, plainMethods));
  }

  private static List<Mismatch> checkThirdPartyTypeMatch(
      ClassRef reference, TypeDescription typeOnClasspath) {
    List<Mismatch> mismatches = Collections.emptyList();

    for (Flag flag : reference.getFlags()) {
      if (!flag.matches(typeOnClasspath.getActualModifiers(false))) {
        String desc = reference.getClassName();
        mismatches =
            add(
                mismatches,
                new Mismatch.MissingFlag(
                    reference.getSources(), desc, flag, typeOnClasspath.getActualModifiers(false)));
      }
    }

    for (FieldRef fieldRef : reference.getFields()) {
      FieldDescription.InDefinedShape fieldDescription = findField(fieldRef, typeOnClasspath);
      if (fieldDescription == null) {
        mismatches = add(mismatches, new Mismatch.MissingField(reference, fieldRef));
      } else {
        for (Flag flag : fieldRef.getFlags()) {
          if (!flag.matches(fieldDescription.getModifiers())) {
            String desc =
                reference.getClassName()
                    + "#"
                    + fieldRef.getName()
                    + Type.getType(fieldRef.getDescriptor()).getInternalName();
            mismatches =
                add(
                    mismatches,
                    new Mismatch.MissingFlag(
                        fieldRef.getSources(), desc, flag, fieldDescription.getModifiers()));
          }
        }
      }
    }

    for (MethodRef methodRef : reference.getMethods()) {
      MethodDescription.InDefinedShape methodDescription = findMethod(methodRef, typeOnClasspath);
      if (methodDescription == null) {
        mismatches = add(mismatches, new Mismatch.MissingMethod(reference, methodRef));
      } else {
        for (Flag flag : methodRef.getFlags()) {
          if (!flag.matches(methodDescription.getModifiers())) {
            String desc =
                reference.getClassName() + "#" + methodRef.getName() + methodRef.getDescriptor();
            mismatches =
                add(
                    mismatches,
                    new Mismatch.MissingFlag(
                        methodRef.getSources(), desc, flag, methodDescription.getModifiers()));
          }
        }
      }
    }
    return mismatches;
  }

  private static FieldDescription.InDefinedShape findField(
      FieldRef fieldRef, TypeDescription typeOnClasspath) {
    for (FieldDescription.InDefinedShape fieldType : typeOnClasspath.getDeclaredFields()) {
      if (fieldType.getName().equals(fieldRef.getName())
          && fieldType.getDescriptor().equals(fieldRef.getDescriptor())) {
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
      MethodRef methodRef, TypeDescription typeOnClasspath) {
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
  static List<Mismatch> add(List<Mismatch> mismatches, Mismatch mismatch) {
    List<Mismatch> result = mismatches.isEmpty() ? new ArrayList<>() : mismatches;
    result.add(mismatch);
    return result;
  }

  // optimization to avoid ArrayList allocation in the common case when there are no mismatches
  static List<Mismatch> addAll(List<Mismatch> mismatches, List<Mismatch> toAdd) {
    if (!toAdd.isEmpty()) {
      List<Mismatch> result = mismatches.isEmpty() ? new ArrayList<>() : mismatches;
      result.addAll(toAdd);
      return result;
    }
    return mismatches;
  }
}
