/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static io.opentelemetry.javaagent.tooling.instrumentation.indy.ForceDynamicallyTypedAssignReturnedFactory.replaceAnnotationValue;

import javax.annotation.Nonnull;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.jetbrains.annotations.NotNull;

/**
 * Pool strategy that sets "inline" attribute to false on {@link Advice.OnMethodEnter} and {@link
 * Advice.OnMethodExit} annotations.
 */
class AdviceUninliningPoolStrategy implements AgentBuilder.PoolStrategy {
  private final AgentBuilder.PoolStrategy poolStrategy;

  public AdviceUninliningPoolStrategy(AgentBuilder.PoolStrategy poolStrategy) {
    this.poolStrategy = poolStrategy;
  }

  @NotNull
  @Override
  public TypePool typePool(@NotNull ClassFileLocator classFileLocator, ClassLoader classLoader) {
    TypePool typePool = poolStrategy.typePool(classFileLocator, classLoader);
    return new TypePoolWrapper(typePool);
  }

  @NotNull
  @Override
  public TypePool typePool(
      @NotNull ClassFileLocator classFileLocator, ClassLoader classLoader, @NotNull String name) {
    TypePool typePool = poolStrategy.typePool(classFileLocator, classLoader, name);
    return new TypePoolWrapper(typePool);
  }

  private static class TypePoolWrapper implements TypePool {
    private final TypePool typePool;

    public TypePoolWrapper(TypePool typePool) {
      this.typePool = typePool;
    }

    @NotNull
    @Override
    public Resolution describe(@NotNull String name) {
      Resolution resolution = typePool.describe(name);

      return new Resolution() {
        @Override
        public boolean isResolved() {
          return resolution.isResolved();
        }

        @NotNull
        @Override
        public TypeDescription resolve() {
          TypeDescription typeDescription = resolution.resolve();

          return new TypeDescription.AbstractBase.OfSimpleType.WithDelegation() {

            @NotNull
            @Override
            public String getName() {
              return typeDescription.getName();
            }

            @NotNull
            @Override
            protected TypeDescription delegate() {
              return typeDescription;
            }

            @NotNull
            @Override
            public MethodList<MethodDescription.InDefinedShape> getDeclaredMethods() {
              MethodList<MethodDescription.InDefinedShape> methods = super.getDeclaredMethods();

              class MethodListWrapper
                  extends MethodList.AbstractBase<MethodDescription.InDefinedShape> {

                @Override
                public MethodDescription.InDefinedShape get(int index) {
                  return new MethodDescriptionWrapper(methods.get(index));
                }

                @Override
                public int size() {
                  return methods.size();
                }
              }

              return new MethodListWrapper();
            }
          };
        }
      };
    }

    @Override
    public void clear() {
      typePool.clear();
    }
  }

  private static class MethodDescriptionWrapper extends DelegatingMethodDescription {

    MethodDescriptionWrapper(MethodDescription.InDefinedShape method) {
      super(method);
    }

    @NotNull
    @Override
    public AnnotationList getDeclaredAnnotations() {
      AnnotationList annotations = method.getDeclaredAnnotations();

      class AnnotationListWrapper extends AnnotationList.AbstractBase {

        @Override
        public AnnotationDescription get(int index) {
          AnnotationDescription annotation = annotations.get(index);
          String annotationTypeName = annotation.getAnnotationType().getActualName();
          // we are only interested in OnMethodEnter and OnMethodExit annotations
          if (!Advice.OnMethodEnter.class.getName().equals(annotationTypeName)
              && !Advice.OnMethodExit.class.getName().equals(annotationTypeName)) {
            return annotation;
          }

          // replace value for "inline" attribute with false
          return replaceAnnotationValue(
              annotation, "inline", oldVal -> AnnotationValue.ForConstant.of(false));
        }

        @Override
        public int size() {
          return annotations.size();
        }
      }

      return new AnnotationListWrapper();
    }
  }

  private static class DelegatingMethodDescription
      extends MethodDescription.InDefinedShape.AbstractBase {
    protected final MethodDescription.InDefinedShape method;

    DelegatingMethodDescription(MethodDescription.InDefinedShape method) {
      this.method = method;
    }

    @Nonnull
    @Override
    public TypeDescription getDeclaringType() {
      return method.getDeclaringType();
    }

    @NotNull
    @Override
    public TypeDescription.Generic getReturnType() {
      return method.getReturnType();
    }

    @NotNull
    @Override
    public ParameterList<ParameterDescription.InDefinedShape> getParameters() {
      return method.getParameters();
    }

    @NotNull
    @Override
    public TypeList.Generic getExceptionTypes() {
      return method.getExceptionTypes();
    }

    @Override
    public AnnotationValue<?, ?> getDefaultValue() {
      return method.getDefaultValue();
    }

    @NotNull
    @Override
    public String getInternalName() {
      return method.getInternalName();
    }

    @NotNull
    @Override
    public TypeList.Generic getTypeVariables() {
      return method.getTypeVariables();
    }

    @Override
    public int getModifiers() {
      return method.getModifiers();
    }

    @NotNull
    @Override
    public AnnotationList getDeclaredAnnotations() {
      return method.getDeclaredAnnotations();
    }
  }
}
