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
        metadata.getAnnotationAttributes(ConditionalOnEnabledInstrumentation.class.getName());

    String name = String.format("otel.instrumentation.%s.enabled", attributes.get("module"));
    Boolean explicit = context.getEnvironment().getProperty(name, Boolean.class);
    if (explicit != null) {
      return explicit;
    }
    boolean defaultValue = (boolean) attributes.get("enabledByDefault");
    if (!defaultValue) {
      return false;
    }
    return context
        .getEnvironment()
        .getProperty("otel.instrumentation.common.default-enabled", Boolean.class, true);
  }
}
