/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6;

import application.io.opentelemetry.instrumentation.annotations.MetricAttribute;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.annotation.support.SpanAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.instrumentationannotations.MethodRequest;
import java.lang.reflect.Parameter;
import javax.annotation.Nullable;

public abstract class MetricsAnnotationHelper {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-2.6";
  static final Meter METER = GlobalOpenTelemetry.get().getMeter(INSTRUMENTATION_NAME);
  private static final SpanAttributesExtractor PARAMETER_ATTRIBUTES = createAttributesExtractor();

  static void extractMetricAttributes(
      MethodRequest methodRequest, AttributesBuilder attributesBuilder) {
    attributesBuilder.putAll(
        PARAMETER_ATTRIBUTES.extract(methodRequest.method(), methodRequest.args()));
  }

  static void extractAdditionAttributes(String[] attributes, AttributesBuilder attributesBuilder) {
    int length = attributes.length;
    for (int i = 0; i + 1 < length; i += 2) {
      attributesBuilder.put(attributes[i], attributes[i + 1]);
    }
  }

  private static SpanAttributesExtractor createAttributesExtractor() {
    return SpanAttributesExtractor.create(
        (method, parameters) -> {
          String[] attributeNames = new String[parameters.length];
          for (int i = 0; i < parameters.length; i++) {
            attributeNames[i] = attributeName(parameters[i]);
          }
          return attributeNames;
        });
  }

  @Nullable
  private static String attributeName(Parameter parameter) {
    MetricAttribute annotation = parameter.getDeclaredAnnotation(MetricAttribute.class);
    if (annotation == null) {
      return null;
    }
    String value = annotation.value();
    if (!value.isEmpty()) {
      return value;
    } else if (parameter.isNamePresent()) {
      return parameter.getName();
    } else {
      return null;
    }
  }
}
