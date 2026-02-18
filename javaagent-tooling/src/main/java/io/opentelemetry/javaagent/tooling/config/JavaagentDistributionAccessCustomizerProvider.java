/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.DistributionModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.DistributionPropertyModel;

/**
 * Allows access to the Javaagent distribution node, which cannot be accessed using the {@link
 * ConfigProvider} API.
 */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public final class JavaagentDistributionAccessCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          DistributionModel distribution = model.getDistribution();
          if (distribution != null) {
            DistributionPropertyModel javaagent =
                distribution.getAdditionalProperties().get("javaagent");
            if (javaagent != null) {
              AgentDistributionConfig.set(
                  MAPPER.convertValue(javaagent, AgentDistributionConfig.class));
            }
          }
          return model;
        });
  }
}
