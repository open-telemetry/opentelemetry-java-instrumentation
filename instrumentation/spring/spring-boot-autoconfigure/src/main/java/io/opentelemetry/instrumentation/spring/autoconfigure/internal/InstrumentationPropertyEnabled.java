/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Objects;
import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
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

    String name = requireNonNull(attributes.get("module")).toString();
    boolean enabledByDefault = (boolean) requireNonNull(attributes.get("enabledByDefault"));
    EnabledInstrumentations enabledInstrumentations = EarlyConfig.getEnabledInstrumentations(
        (ConfigurableEnvironment) context.getEnvironment());

    Boolean enabled = enabledInstrumentations.getEnabled(name);
    if (enabled != null) {
      return enabled;
    }
    return enabledByDefault && enabledInstrumentations.isDefaultEnabled();
  }
}
