/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.matcher;

import static net.bytebuddy.description.method.MethodDescription.CONSTRUCTOR_INTERNAL_NAME;

import io.opentelemetry.javaagent.extension.muzzle.Reference;
import io.opentelemetry.javaagent.extension.muzzle.Reference.Flag.ManifestationFlag;
import io.opentelemetry.javaagent.extension.muzzle.Reference.Flag.OwnershipFlag;
import io.opentelemetry.javaagent.extension.muzzle.Reference.Flag.VisibilityFlag;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.Resolution;

/** This class provides a common interface for {@link Reference} and {@link TypeDescription}. */
interface HelperReferenceWrapper {
  boolean isAbstract();

  /**
   * Returns true if the wrapped type extends any class other than {@link Object} or implements any
   * interface.
   */
  boolean hasSuperTypes();

  /**
   * Returns an iterable containing the wrapped type's super class (if exists) and implemented
   * interfaces.
   */
  Stream<HelperReferenceWrapper> getSuperTypes();

  /** Returns an iterable with all non-private, non-static methods declared in the wrapped type. */
  Stream<Method> getMethods();

  /** Returns an iterable with all non-private fields declared in the wrapped type. */
  Stream<Field> getFields();

  final class Method {
    private final boolean isAbstract;
    private final String declaringClass;
    private final String name;
    private final String descriptor;

    public Method(boolean isAbstract, String declaringClass, String name, String descriptor) {
      this.isAbstract = isAbstract;
      this.declaringClass = declaringClass;
      this.name = name;
      this.descriptor = descriptor;
    }

    public boolean isAbstract() {
      return isAbstract;
    }

    public String getDeclaringClass() {
      return declaringClass;
    }

    public String getName() {
      return name;
    }

    public String getDescriptor() {
      return descriptor;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof Method)) {
        return false;
      }
      Method other = (Method) obj;
      return Objects.equals(name, other.name) && Objects.equals(descriptor, other.descriptor);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, descriptor);
    }
  }

  final class Field {
    private final String name;
    private final String descriptor;

    public Field(String name, String descriptor) {
      this.name = name;
      this.descriptor = descriptor;
    }

    public String getName() {
      return name;
    }

    public String getDescriptor() {
      return descriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Field field = (Field) o;
      return Objects.equals(name, field.name) && Objects.equals(descriptor, field.descriptor);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, descriptor);
    }
  }

  class Factory {
    private final TypePool classpathPool;
    private final Map<String, Reference> helperReferences;

    public Factory(TypePool classpathPool, Map<String, Reference> helperReferences) {
      this.classpathPool = classpathPool;
      this.helperReferences = helperReferences;
    }

    public HelperReferenceWrapper create(Reference reference) {
      return new ReferenceType(reference);
    }

    private HelperReferenceWrapper create(String className) {
      Resolution resolution = classpathPool.describe(className);
      if (resolution.isResolved()) {
        return new ClasspathType(resolution.resolve());
      }
      if (helperReferences.containsKey(className)) {
        return new ReferenceType(helperReferences.get(className));
      }
      throw new IllegalStateException("Missing class " + className);
    }

    private final class ReferenceType implements HelperReferenceWrapper {
      private final Reference reference;

      private ReferenceType(Reference reference) {
        this.reference = reference;
      }

      @Override
      public boolean isAbstract() {
        return reference.getFlags().contains(ManifestationFlag.ABSTRACT);
      }

      @Override
      public boolean hasSuperTypes() {
        return hasActualSuperType() || reference.getInterfaces().size() > 0;
      }

      @Override
      public Stream<HelperReferenceWrapper> getSuperTypes() {
        Stream<HelperReferenceWrapper> superClass = Stream.empty();
        if (hasActualSuperType()) {
          superClass = Stream.of(Factory.this.create(reference.getSuperName()));
        }

        Stream<HelperReferenceWrapper> interfaces =
            reference.getInterfaces().stream().map(Factory.this::create);

        return Stream.concat(superClass, interfaces);
      }

      private boolean hasActualSuperType() {
        return reference.getSuperName() != null;
      }

      @Override
      public Stream<Method> getMethods() {
        return reference.getMethods().stream().filter(this::isOverrideable).map(this::toMethod);
      }

      private boolean isOverrideable(Reference.Method method) {
        return !(method.getFlags().contains(OwnershipFlag.STATIC)
            || method.getFlags().contains(VisibilityFlag.PRIVATE)
            || CONSTRUCTOR_INTERNAL_NAME.equals(method.getName()));
      }

      private Method toMethod(Reference.Method method) {
        return new Method(
            method.getFlags().contains(ManifestationFlag.ABSTRACT),
            reference.getClassName(),
            method.getName(),
            method.getDescriptor());
      }

      @Override
      public Stream<Field> getFields() {
        return reference.getFields().stream()
            .filter(this::isDeclaredAndNotPrivate)
            .map(this::toField);
      }

      private boolean isDeclaredAndNotPrivate(Reference.Field field) {
        return field.isDeclared() && !field.getFlags().contains(VisibilityFlag.PRIVATE);
      }

      private Field toField(Reference.Field field) {
        return new Field(field.getName(), field.getType().getDescriptor());
      }
    }

    private static final class ClasspathType implements HelperReferenceWrapper {
      private final TypeDescription type;

      private ClasspathType(TypeDescription type) {
        this.type = type;
      }

      @Override
      public boolean isAbstract() {
        return type.isAbstract();
      }

      @Override
      public boolean hasSuperTypes() {
        return hasActualSuperType() || type.getInterfaces().size() > 0;
      }

      private boolean hasActualSuperType() {
        return type.getSuperClass() != null;
      }

      @Override
      public Stream<HelperReferenceWrapper> getSuperTypes() {
        Stream<HelperReferenceWrapper> superClass = Stream.empty();
        if (hasActualSuperType()) {
          superClass = Stream.of(new ClasspathType(type.getSuperClass().asErasure()));
        }

        Stream<HelperReferenceWrapper> interfaces =
            type.getInterfaces().asErasures().stream().map(ClasspathType::new);

        return Stream.concat(superClass, interfaces);
      }

      @Override
      public Stream<Method> getMethods() {
        return type.getDeclaredMethods().stream().filter(this::isOverrideable).map(this::toMethod);
      }

      private boolean isOverrideable(InDefinedShape method) {
        return !(method.isStatic() || method.isPrivate() || method.isConstructor());
      }

      private Method toMethod(InDefinedShape method) {
        return new Method(
            method.isAbstract(), type.getName(), method.getInternalName(), method.getDescriptor());
      }

      @Override
      public Stream<Field> getFields() {
        return type.getDeclaredFields().stream().filter(this::isNotPrivate).map(this::toField);
      }

      private boolean isNotPrivate(FieldDescription.InDefinedShape field) {
        return !field.isPrivate();
      }

      private Field toField(FieldDescription.InDefinedShape field) {
        return new Field(field.getName(), field.getDescriptor());
      }
    }
  }
}
