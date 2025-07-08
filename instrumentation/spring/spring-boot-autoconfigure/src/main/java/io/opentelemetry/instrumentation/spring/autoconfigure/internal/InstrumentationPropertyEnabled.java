/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static io.opentelemetry.instrumentation.spring.autoconfigure.internal.EarlyConfig.translatePropertyName;

import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationPropertyEnabled implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Map<String, Object> attributes =
        Objects.requireNonNull(
            metadata.getAnnotationAttributes(ConditionalOnEnabledInstrumentation.class.getName()));

    Environment environment = context.getEnvironment();

    String name =
        String.format(
            translatePropertyName(environment, "otel.instrumentation.%s.enabled"),
            attributes.get("module"));
    Boolean explicit = environment.getProperty(name, Boolean.class);
    if (explicit != null) {
      return explicit;
    }
    boolean defaultValue = (boolean) attributes.get("enabledByDefault");
    if (!defaultValue) {
      return false;
    }
    return environment.getProperty(
        translatePropertyName(environment, "otel.instrumentation.common.default-enabled"),
        Boolean.class,
        true);
  }
}
