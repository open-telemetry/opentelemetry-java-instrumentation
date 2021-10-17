/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import java.lang.annotation.Annotation;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Helper class for reflecting over annotations at runtime.. */
public class AnnotationReflectionHelper {
  private AnnotationReflectionHelper() {}

  /**
   * Returns the {@link Class Class&lt;? extends Annotation&gt;} for the name of the {@link
   * Annotation} at runtime, otherwise returns {@code null}.
   */
  @Nullable
  public static Class<? extends Annotation> forNameOrNull(
      ClassLoader classLoader, String className) {
    try {
      return Class.forName(className, true, classLoader).asSubclass(Annotation.class);
    } catch (ClassNotFoundException | ClassCastException exception) {
      return null;
    }
  }

  /**
   * Binds a lambda of the functional interface {@link Function Function&lt;A extends Annotation,
   * T&gt;} to the element of an {@link Annotation} class by name which, when invoked with an
   * instance of that annotation, will return the value of that element.
   *
   * <p>For example, calling this method as follows:
   *
   * <pre>{@code
   * Function<WithSpan, String> function = AnnotationReflectionHelper.bindAnnotationElementMethod(
   *     MethodHandles.lookup(),
   *     WithSpan.class,
   *     "value",
   *     String.class);
   * }</pre>
   *
   * <p>is equivalent to the following Java code:
   *
   * <pre>{@code
   * Function<WithSpan, String> function = WithSpan::value;
   * }</pre>
   *
   * @param lookup the {@link MethodHandles.Lookup} of the calling method, e.g. {@link
   *     MethodHandles#lookup()}
   * @param annotationClass the {@link Class} of the {@link Annotation}
   * @param methodName name of the annotation element method
   * @param returnClass type of the annotation element
   * @param <A> the type of the annotation
   * @param <T> the type of the annotation element
   * @return Instance of {@link Function Function&lt;Annotation, T&gt;} that is bound to the
   *     annotation element method
   * @throws NoSuchMethodException the annotation element method was not found
   * @throws Throwable on failing to bind to the
   */
  public static <A extends Annotation, T> Function<A, T> bindAnnotationElementMethod(
      MethodHandles.Lookup lookup,
      Class<? extends Annotation> annotationClass,
      String methodName,
      Class<T> returnClass)
      throws Throwable {

    MethodHandle valueHandle =
        lookup.findVirtual(annotationClass, methodName, MethodType.methodType(returnClass));

    CallSite callSite =
        LambdaMetafactory.metafactory(
            lookup,
            "apply",
            MethodType.methodType(Function.class),
            MethodType.methodType(Object.class, Object.class),
            valueHandle,
            MethodType.methodType(returnClass, annotationClass));

    MethodHandle factory = callSite.getTarget();

    @SuppressWarnings("unchecked")
    Function<A, T> function = (Function<A, T>) factory.invoke();
    return function;
  }
}
