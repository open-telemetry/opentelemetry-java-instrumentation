/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(name = "otel.instrumentation.kafka.enabled", matchIfMissing = true)
@Conditional({SdkEnabled.class, InstrumentationPropertyEnabled.class})
public @interface SpringBootInstrumentation {
  String value();
}
