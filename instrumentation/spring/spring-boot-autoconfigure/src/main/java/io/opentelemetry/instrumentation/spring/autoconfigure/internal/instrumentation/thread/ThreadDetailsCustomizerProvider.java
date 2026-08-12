/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.instrumentation.thread.internal.AbstractThreadDetailsCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.DistributionModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.DistributionPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;

/**
 * Adds thread details span attributes when enabled via the {@code distribution.spring_starter}
 * node.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ThreadDetailsCustomizerProvider extends AbstractThreadDetailsCustomizerProvider {

  @Override
  protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
    DistributionModel distribution = model.getDistribution();
    if (distribution == null) {
      return false;
    }
    DistributionPropertyModel springStarter =
        distribution.getAdditionalProperties().get("spring_starter");
    if (springStarter == null) {
      return false;
    }
    Object enabled = springStarter.getAdditionalProperties().get("thread_details_enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) enabled;
    }
    // a String when set via environment variable substitution (thread_details_enabled: ${OTEL_...})
    return enabled instanceof String && Boolean.parseBoolean((String) enabled);
  }
}
