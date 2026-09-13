/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.stream.Collectors.joining;

import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.FieldRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.MethodRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Source;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/**
 * Detects instrumentation helper classes that rely on same-package access to non-public library
 * code.
 *
 * <p>An ordinary jar can become an automatic named module at runtime (or be loaded by a class
 * loader/module that does not open the package to unnamed code), in which case a helper class that
 * happens to share a package name with a library class it instruments is <em>not</em> guaranteed to
 * be granted package (or protected) access to that library's non-public classes, constructors,
 * methods or fields: the two classes are defined by different class loaders (and potentially
 * different modules), so they do not actually share a runtime package/module even though their
 * binary names look alike.
 *
 * <p>References made by inlined advice methods are exempt because that bytecode executes from the
 * instrumented (library) class itself, so it genuinely executes as if it were part of the library's
 * package.
 *
 * <p>This class only runs at compile (muzzle generation) time. It does not affect the generated
 * muzzle references or the runtime muzzle matching behavior.
 */
final class SamePackageAccessValidator {

  private final HelperClassPredicate helperClassPredicate;
  private final ClassLoader classLoader;

  SamePackageAccessValidator(HelperClassPredicate helperClassPredicate, ClassLoader classLoader) {
    this.helperClassPredicate = helperClassPredicate;
    this.classLoader = classLoader;
  }

  /**
   * Validates that no helper class in {@code references} relies on same-package access to
   * non-public library code.
   *
   * @throws MuzzleCompilationException aggregating every violation found, if any.
   */
  void validate(Map<String, ClassRef> references) {
    List<String> violations = new ArrayList<>();
    for (ClassRef reference : references.values()) {
      if (!helperClassPredicate.isLibraryClass(reference.getClassName())) {
        // only library targets are subject to this check
        continue;
      }
      violations.addAll(validateClass(reference));
    }
    // deterministic order regardless of hash-based iteration upstream
    violations.sort(Comparator.naturalOrder());

    if (!violations.isEmpty()) {
      throw new MuzzleCompilationException(buildMessage(violations));
    }
  }

  private List<String> validateClass(ClassRef reference) {
    List<String> violations = new ArrayList<>();
    String targetClassName = reference.getClassName();
    String targetPackage = packageName(targetClassName);

    for (Source source : reference.getSources()) {
      if (!isEnforcedSource(source, targetPackage)) {
        continue;
      }
      Class<?> targetClass = tryLoadClass(targetClassName);
      if (targetClass != null && !Modifier.isPublic(targetClass.getModifiers())) {
        violations.add(describe(source, "class " + targetClassName + " is not public"));
      }
    }

    Class<?> targetClass = tryLoadClass(targetClassName);
    if (targetClass == null) {
      // can't resolve the target class on the generation classpath - nothing more to validate
      return violations;
    }

    for (FieldRef field : reference.getFields()) {
      for (Source source : field.getSources()) {
        if (!isEnforcedSource(source, targetPackage)) {
          continue;
        }
        Field resolved = findField(targetClass, field.getName(), field.getDescriptor());
        if (resolved != null && !Modifier.isPublic(resolved.getModifiers())) {
          violations.add(
              describe(
                  source, "field " + targetClassName + "#" + field.getName() + " is not public"));
        }
      }
    }

    for (MethodRef method : reference.getMethods()) {
      for (Source source : method.getSources()) {
        if (!isEnforcedSource(source, targetPackage)) {
          continue;
        }
        Executable resolved = findMethod(targetClass, method.getName(), method.getDescriptor());
        if (resolved != null && !Modifier.isPublic(resolved.getModifiers())) {
          String kind = method.isConstructor() ? "constructor" : "method";
          violations.add(
              describe(
                  source,
                  kind
                      + " "
                      + targetClassName
                      + "#"
                      + method.getName()
                      + method.getDescriptor()
                      + " is not public"));
        }
      }
    }

    return violations;
  }

  /**
   * Returns whether {@code source} is subject to this check: it must not be part of an inlined
   * advice method, and it must be in the same package as the referenced target.
   */
  private static boolean isEnforcedSource(Source source, String targetPackage) {
    if (source.isInlinedAdvice()) {
      return false;
    }
    return packageName(source.getName()).equals(targetPackage);
  }

  @Nullable
  private Class<?> tryLoadClass(String className) {
    try {
      return Class.forName(className, false, classLoader);
    } catch (ClassNotFoundException | LinkageError ignored) {
      // not resolvable on the generation classpath; nothing we can validate
      return null;
    }
  }

  @Nullable
  private static Field findField(@Nullable Class<?> clazz, String name, String descriptor) {
    if (clazz == null) {
      return null;
    }
    try {
      Field field = clazz.getDeclaredField(name);
      if (Type.getDescriptor(field.getType()).equals(descriptor)) {
        return field;
      }
    } catch (NoSuchFieldException e) {
      // keep looking in the type hierarchy
    }
    Field fromSuperClass = findField(clazz.getSuperclass(), name, descriptor);
    if (fromSuperClass != null) {
      return fromSuperClass;
    }
    for (Class<?> interfaceClass : clazz.getInterfaces()) {
      Field fromInterface = findField(interfaceClass, name, descriptor);
      if (fromInterface != null) {
        return fromInterface;
      }
    }
    return null;
  }

  @Nullable
  private Executable findMethod(@Nullable Class<?> clazz, String name, String descriptor) {
    Type methodType = Type.getMethodType(descriptor);
    Class<?>[] parameterTypes;
    try {
      parameterTypes = resolveParameterTypes(methodType);
    } catch (ClassNotFoundException | LinkageError ignored) {
      return null;
    }

    if ("<init>".equals(name)) {
      try {
        return clazz == null ? null : clazz.getDeclaredConstructor(parameterTypes);
      } catch (NoSuchMethodException e) {
        return null;
      }
    }
    return findMethod(clazz, name, parameterTypes);
  }

  @Nullable
  private static Method findMethod(
      @Nullable Class<?> clazz, String name, Class<?>[] parameterTypes) {
    if (clazz == null) {
      return null;
    }
    try {
      return clazz.getDeclaredMethod(name, parameterTypes);
    } catch (NoSuchMethodException e) {
      Method fromSuperClass = findMethod(clazz.getSuperclass(), name, parameterTypes);
      if (fromSuperClass != null) {
        return fromSuperClass;
      }
      for (Class<?> interfaceClass : clazz.getInterfaces()) {
        Method fromInterface = findMethod(interfaceClass, name, parameterTypes);
        if (fromInterface != null) {
          return fromInterface;
        }
      }
      return null;
    }
  }

  private Class<?>[] resolveParameterTypes(Type methodType) throws ClassNotFoundException {
    Type[] argumentTypes = methodType.getArgumentTypes();
    Class<?>[] parameterTypes = new Class<?>[argumentTypes.length];
    for (int i = 0; i < argumentTypes.length; i++) {
      parameterTypes[i] = resolveType(argumentTypes[i]);
    }
    return parameterTypes;
  }

  private Class<?> resolveType(Type type) throws ClassNotFoundException {
    switch (type.getSort()) {
      case Type.VOID:
        return void.class;
      case Type.BOOLEAN:
        return boolean.class;
      case Type.CHAR:
        return char.class;
      case Type.BYTE:
        return byte.class;
      case Type.SHORT:
        return short.class;
      case Type.INT:
        return int.class;
      case Type.FLOAT:
        return float.class;
      case Type.LONG:
        return long.class;
      case Type.DOUBLE:
        return double.class;
      case Type.ARRAY:
        return Class.forName(type.getDescriptor().replace('/', '.'), false, classLoader);
      default:
        return Class.forName(type.getClassName(), false, classLoader);
    }
  }

  private static String describe(Source source, String description) {
    String location =
        source.getLine() > 0 ? source.getName() + ":" + source.getLine() : source.getName();
    return location + " -> " + description;
  }

  private static String packageName(String className) {
    int index = className.lastIndexOf('.');
    return index == -1 ? "" : className.substring(0, index);
  }

  private static String buildMessage(List<String> violations) {
    return violations.stream()
        .collect(
            joining(
                "\n  ",
                "Found instrumentation helper classes relying on same-package access to"
                    + " non-public library code. This does not work reliably because an ordinary"
                    + " jar can be loaded as an automatic named module, in which case the helper"
                    + " class (loaded by a different class loader/module) does not actually share"
                    + " a runtime package with the library class, even though their binary names"
                    + " look alike. Access the library through a public API instead (add one"
                    + " upstream if necessary), or move the bridge code into an "
                    + "io.opentelemetry.* package and access the non-public member reflectively:"
                    + "\n  ",
                ""));
  }
}
