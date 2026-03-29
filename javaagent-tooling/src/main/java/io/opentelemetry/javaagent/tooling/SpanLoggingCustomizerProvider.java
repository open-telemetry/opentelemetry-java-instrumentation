/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.logging.internal.AbstractSpanLoggingCustomizerProvider;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;

/** Adds span logging exporter for debug mode */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public class SpanLoggingCustomizerProvider extends AbstractSpanLoggingCustomizerProvider {

  @Override
  protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
    return EarlyInitAgentConfig.get().isDebug();
  }
}
