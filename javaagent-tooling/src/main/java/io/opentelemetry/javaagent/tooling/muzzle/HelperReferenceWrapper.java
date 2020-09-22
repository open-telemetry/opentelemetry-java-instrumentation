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

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static net.bytebuddy.description.method.MethodDescription.CONSTRUCTOR_INTERNAL_NAME;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.Resolution;

/** This class provides a common interface for {@link Reference} and {@link TypeDescription}. */
public interface HelperReferenceWrapper {
  boolean isAbstract();

  /**
   * @return true if the wrapped type extends any class other than {@link Object} or implements any
   *     interface.
   */
  boolean hasSuperTypes();

  /**
   * @return An iterable containing the wrapped type's super class (if exists) and implemented
   *     interfaces.
   */
  Iterable<HelperReferenceWrapper> getSuperTypes();

  /** @return An iterable with all non-private, non-static methods declared in the wrapped type. */
  Iterable<Method> getMethods();

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
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Method method = (Method) o;
      return Objects.equal(name, method.name) && Objects.equal(descriptor, method.descriptor);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name, descriptor);
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
        return reference.getFlags().contains(Flag.ABSTRACT);
      }

      @Override
      public boolean hasSuperTypes() {
        return hasActualSuperType() || reference.getInterfaces().size() > 0;
      }

      // Uses guava iterables to avoid unnecessary collection copying
      @Override
      public Iterable<HelperReferenceWrapper> getSuperTypes() {
        Iterable<HelperReferenceWrapper> superClass = emptyList();
        if (hasActualSuperType()) {
          superClass = singleton(Factory.this.create(reference.getSuperName()));
        }

        Iterable<HelperReferenceWrapper> interfaces =
            FluentIterable.from(reference.getInterfaces()).transform(toWrapper());

        return Iterables.concat(superClass, interfaces);
      }

      private boolean hasActualSuperType() {
        return reference.getSuperName() != null;
      }

      private Function<String, HelperReferenceWrapper> toWrapper() {
        return new Function<String, HelperReferenceWrapper>() {
          @Override
          public HelperReferenceWrapper apply(String interfaceName) {
            return Factory.this.create(interfaceName);
          }
        };
      }

      // Uses guava iterables to avoid unnecessary collection copying
      @Override
      public Iterable<Method> getMethods() {
        return FluentIterable.from(reference.getMethods())
            .filter(isOverrideable())
            .transform(toMethod());
      }

      private Predicate<Reference.Method> isOverrideable() {
        return new Predicate<Reference.Method>() {
          @Override
          public boolean apply(Reference.Method input) {
            return !(input.getFlags().contains(Flag.STATIC)
                || input.getFlags().contains(Flag.PRIVATE)
                || CONSTRUCTOR_INTERNAL_NAME.equals(input.getName()));
          }
        };
      }

      private Function<Reference.Method, Method> toMethod() {
        return new Function<Reference.Method, Method>() {
          @Override
          public Method apply(Reference.Method method) {
            return new Method(
                method.getFlags().contains(Flag.ABSTRACT),
                reference.getClassName(),
                method.getName(),
                method.getDescriptor());
          }
        };
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

      // Uses guava iterables to avoid unnecessary collection copying
      @Override
      public Iterable<HelperReferenceWrapper> getSuperTypes() {
        Iterable<HelperReferenceWrapper> superClass = emptyList();
        if (hasActualSuperType()) {
          superClass =
              singleton(
                  (HelperReferenceWrapper) new ClasspathType(type.getSuperClass().asErasure()));
        }

        Iterable<HelperReferenceWrapper> interfaces =
            Iterables.transform(type.getInterfaces().asErasures(), toWrapper());

        return Iterables.concat(superClass, interfaces);
      }

      private static Function<TypeDescription, HelperReferenceWrapper> toWrapper() {
        return new Function<TypeDescription, HelperReferenceWrapper>() {
          @Override
          public HelperReferenceWrapper apply(TypeDescription input) {
            return new ClasspathType(input);
          }
        };
      }

      // Uses guava iterables to avoid unnecessary collection copying
      @Override
      public Iterable<Method> getMethods() {
        return FluentIterable.from(type.getDeclaredMethods())
            .filter(isOverrideable())
            .transform(toMethod());
      }

      private static Predicate<InDefinedShape> isOverrideable() {
        return new Predicate<InDefinedShape>() {
          @Override
          public boolean apply(InDefinedShape input) {
            return !(input.isStatic() || input.isPrivate() || input.isConstructor());
          }
        };
      }

      private Function<InDefinedShape, Method> toMethod() {
        return new Function<InDefinedShape, Method>() {
          @Override
          public Method apply(InDefinedShape method) {
            return new Method(
                method.isAbstract(),
                type.getName(),
                method.getInternalName(),
                method.getDescriptor());
          }
        };
      }
    }
  }
}
