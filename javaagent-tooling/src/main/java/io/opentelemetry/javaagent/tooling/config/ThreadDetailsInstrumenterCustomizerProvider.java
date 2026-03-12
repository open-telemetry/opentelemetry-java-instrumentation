/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.incubator.thread.ThreadDetailsAttributesExtractor;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;

@AutoService(InstrumenterCustomizerProvider.class)
public class ThreadDetailsInstrumenterCustomizerProvider implements InstrumenterCustomizerProvider {
  @Override
  public void customize(InstrumenterCustomizer customizer) {
    if (AgentDistributionConfig.get().isThreadDetailsEnabled()) {
      customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
    }
  }
}
