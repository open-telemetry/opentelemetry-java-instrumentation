/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class SdkEnabled extends AnyNestedCondition {
  public SdkEnabled() {
    super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "false", matchIfMissing = true)
  static class NotDisabled {}
}
