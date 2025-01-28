/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * This factory is designed to wrap around {@link Advice.PostProcessor.Factory} and ensures that
 * {@link net.bytebuddy.implementation.bytecode.assign.Assigner.Typing#DYNAMIC} is used everywhere.
 *
 * <p>This helps by avoiding errors where the instrumented bytecode is suddenly unloadable due to
 * incompatible assignments and preventing cluttering advice code annotations with the explicit
 * typing.
 */
public class ForceDynamicallyTypedAssignReturnedFactory implements Advice.PostProcessor.Factory {

  private static final String TO_ARGUMENTS_TYPENAME =
      Advice.AssignReturned.ToArguments.class.getName();
  private static final String TO_ARGUMENT_TYPENAME =
      Advice.AssignReturned.ToArguments.ToArgument.class.getName();
  private static final String TO_ALL_ARGUMENTS_TYPENAME =
      Advice.AssignReturned.ToAllArguments.class.getName();
  private static final String TO_THIS_TYPENAME = Advice.AssignReturned.ToThis.class.getName();
  private static final String TO_FIELDS_TYPENAME = Advice.AssignReturned.ToFields.class.getName();
  private static final String TO_FIELD_TYPENAME =
      Advice.AssignReturned.ToFields.ToField.class.getName();
  private static final String TO_RETURNED_TYPENAME =
      Advice.AssignReturned.ToReturned.class.getName();
  private static final String TO_THROWN_TYPENAME = Advice.AssignReturned.ToThrown.class.getName();
  private static final EnumerationDescription DYNAMIC_TYPING =
      new EnumerationDescription.ForLoadedEnumeration(Assigner.Typing.DYNAMIC);

  private final Advice.PostProcessor.Factory delegate;

  public ForceDynamicallyTypedAssignReturnedFactory(Advice.PostProcessor.Factory delegate) {
    this.delegate = delegate;
  }

  @Override
  public Advice.PostProcessor make(
      List<? extends AnnotationDescription> methodAnnotations,
      TypeDescription returnType,
      boolean exit) {
    return delegate.make(forceDynamicTyping(methodAnnotations), returnType, exit);
  }

  // Visible for testing
  static List<? extends AnnotationDescription> forceDynamicTyping(
      List<? extends AnnotationDescription> declaredAnnotations) {
    return declaredAnnotations.stream()
        .map(ForceDynamicallyTypedAssignReturnedFactory::forceDynamicTyping)
        .collect(Collectors.toList());
  }

  private static AnnotationDescription forceDynamicTyping(AnnotationDescription anno) {

    String name = anno.getAnnotationType().getName();
    if (name.equals(TO_FIELD_TYPENAME)
        || name.equals(TO_ARGUMENT_TYPENAME)
        || name.equals(TO_THIS_TYPENAME)
        || name.equals(TO_ALL_ARGUMENTS_TYPENAME)
        || name.equals(TO_RETURNED_TYPENAME)
        || name.equals(TO_THROWN_TYPENAME)) {
      return replaceAnnotationValue(
          anno, "typing", oldVal -> AnnotationValue.ForEnumerationDescription.of(DYNAMIC_TYPING));
    } else if (name.equals(TO_FIELDS_TYPENAME) || name.equals(TO_ARGUMENTS_TYPENAME)) {
      return replaceAnnotationValue(
          anno,
          "value",
          oldVal -> {
            if (!oldVal.getState().isDefined()) {
              return null;
            }
            AnnotationDescription[] resolve = (AnnotationDescription[]) oldVal.resolve();
            if (resolve.length == 0) {
              return oldVal;
            }
            AnnotationDescription[] newValueList =
                Arrays.stream(resolve)
                    .map(ForceDynamicallyTypedAssignReturnedFactory::forceDynamicTyping)
                    .toArray(AnnotationDescription[]::new);
            TypeDescription subType = newValueList[0].getAnnotationType();
            return AnnotationValue.ForDescriptionArray.of(subType, newValueList);
          });
    }
    return anno;
  }

  private static AnnotationDescription replaceAnnotationValue(
      AnnotationDescription anno,
      String propertyName,
      Function<AnnotationValue<?, ?>, AnnotationValue<?, ?>> valueMapper) {
    AnnotationValue<?, ?> oldValue = anno.getValue(propertyName);
    AnnotationValue<?, ?> newValue = valueMapper.apply(oldValue);
    Map<String, AnnotationValue<?, ?>> updatedValues = new HashMap<>();
    for (MethodDescription.InDefinedShape property :
        anno.getAnnotationType().getDeclaredMethods()) {
      AnnotationValue<?, ?> value = anno.getValue(property);
      if (!propertyName.equals(property.getName()) && value.getState().isDefined()) {
        updatedValues.put(property.getName(), value);
      }
    }
    if (newValue != null) {
      updatedValues.put(propertyName, newValue);
    }
    return new AnnotationDescription.Latent(anno.getAnnotationType(), updatedValues) {};
  }
}
