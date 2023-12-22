/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.instrumentationannotations;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

class SpanAttributeUtil {

  static class Parameter {
    final int var;
    final String name;
    final Type type;

    Parameter(int var, String name, Type type) {
      this.var = var;
      this.name = name;
      this.type = type;
    }
  }

  /**
   * Collect method parameters with @SpanAttribute annotation. Span attribute is named based on the
   * value of the annotation or using the parameter name in the source code, if neither is set then
   * the parameter is ignored.
   */
  static List<Parameter> collectAnnotatedParameters(MethodNode source) {
    List<Parameter> annotatedParameters = new ArrayList<>();
    if (source.visibleParameterAnnotations != null) {
      int slot = 1; // this is in slot 0
      Type[] parameterTypes = Type.getArgumentTypes(source.desc);
      for (int i = 0; i < parameterTypes.length; i++) {
        Type type = parameterTypes[i];
        // if current parameter index is equal or larger than the count of annotated parameters
        // we have already checked all the parameters with annotations
        if (i >= source.visibleParameterAnnotations.length) {
          break;
        }
        boolean hasSpanAttributeAnnotation = false;
        String name = getParameterName(source, i);
        List<AnnotationNode> parameterAnnotations = source.visibleParameterAnnotations[i];
        if (parameterAnnotations != null) {
          for (AnnotationNode annotationNode : parameterAnnotations) {
            if ("Lapplication/io/opentelemetry/instrumentation/annotations/SpanAttribute;"
                .equals(annotationNode.desc)) {
              // check whether SpanAttribute annotation has a value, if it has use that as
              // parameter name
              Object attributeValue = getAnnotationValue(annotationNode);
              if (attributeValue instanceof String) {
                name = (String) attributeValue;
              }

              hasSpanAttributeAnnotation = true;
              break;
            }
          }
        }
        if (hasSpanAttributeAnnotation && name != null) {
          annotatedParameters.add(new Parameter(slot, name, type));
        }
        slot += type.getSize();
      }
    }

    return annotatedParameters;
  }

  private static String getParameterName(MethodNode methodNode, int parameter) {
    ParameterNode parameterNode =
        methodNode.parameters != null && methodNode.parameters.size() > parameter
            ? methodNode.parameters.get(parameter)
            : null;
    return parameterNode != null ? parameterNode.name : null;
  }

  private static Object getAnnotationValue(AnnotationNode annotationNode) {
    if (annotationNode.values != null && !annotationNode.values.isEmpty()) {
      List<Object> values = annotationNode.values;
      for (int i = 0; i < values.size(); i += 2) {
        String attributeName = (String) values.get(i);
        Object attributeValue = values.get(i + 1);
        if ("value".equals(attributeName)) {
          return attributeValue;
        }
      }
    }

    return null;
  }

  private SpanAttributeUtil() {}
}
