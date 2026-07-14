/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.thread.internal.AbstractThreadDetailsCustomizerProvider;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;

/** Adds thread details span attributes when enabled via the {@code distribution.javaagent} node. */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public final class ThreadDetailsCustomizerProvider extends AbstractThreadDetailsCustomizerProvider {

  @Override
  public int order() {
    // run after JavaagentDistributionAccessCustomizerProvider (default order) has populated
    // AgentDistributionConfig from the distribution.javaagent node
    return 1;
  }

  @Override
  protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
    return AgentDistributionConfig.get().isThreadDetailsEnabled();
  }
}
