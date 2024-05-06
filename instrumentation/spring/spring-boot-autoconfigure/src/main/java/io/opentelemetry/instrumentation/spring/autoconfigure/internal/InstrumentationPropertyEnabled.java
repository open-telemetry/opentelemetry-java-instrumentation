/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationPropertyEnabled implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Map<String, Object> attributes =
        metadata.getAnnotationAttributes(SpringBootInstrumentation.class.getName());

    String value = String.format("otel.instrumentation.%s.enabled", attributes.get("value"));
    return context.getEnvironment().getProperty(value, Boolean.class, true);
  }
}
