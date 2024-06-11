/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6;

import application.io.opentelemetry.instrumentation.annotations.MetricAttribute;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.instrumentationannotations.MethodRequest;
import java.lang.reflect.Parameter;

public abstract class MetricsAnnotationHelper {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-2.6";
  static final Meter METER = GlobalOpenTelemetry.get().getMeter(INSTRUMENTATION_NAME);

  static void extractMetricAttributes(
      MethodRequest methodRequest, AttributesBuilder attributesBuilder) {
    Parameter[] parameters = methodRequest.method().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].isAnnotationPresent(MetricAttribute.class)) {
        MetricAttribute annotation = parameters[i].getAnnotation(MetricAttribute.class);
        String attributeKey;
        if (!annotation.value().isEmpty()) {
          attributeKey = annotation.value();
        } else if (parameters[i].isNamePresent()) {
          attributeKey = parameters[i].getName();
        } else {
          continue;
        }
        attributesBuilder.put(attributeKey, methodRequest.args()[i].toString());
      }
    }
  }

  static void extractAdditionAttributes(String[] attributes, AttributesBuilder attributesBuilder) {
    int length = attributes.length;
    for (int i = 0; i + 1 < length; i += 2) {
      attributesBuilder.put(attributes[i], attributes[i + 1]);
    }
  }
}
