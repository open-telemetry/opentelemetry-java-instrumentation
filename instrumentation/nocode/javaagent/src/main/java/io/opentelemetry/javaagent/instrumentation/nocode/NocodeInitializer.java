/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import static io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil.getConfig;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeInstrumentationRules;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(BeforeAgentListener.class)
public class NocodeInitializer implements BeforeAgentListener {

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ConfigProperties config = getConfig(autoConfiguredOpenTelemetrySdk);
    NocodeRulesParser parser = new NocodeRulesParser(config);
    NocodeInstrumentationRules.setGlobalRules(parser.getInstrumentationRules());
  }
}
