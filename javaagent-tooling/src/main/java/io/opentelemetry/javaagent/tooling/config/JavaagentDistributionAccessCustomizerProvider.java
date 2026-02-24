/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.logging.Level.WARNING;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.DistributionModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.DistributionPropertyModel;
import java.io.IOException;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Allows access to the Javaagent distribution node, which cannot be accessed using the {@link
 * ConfigProvider} API.
 */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public final class JavaagentDistributionAccessCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  private static final Logger logger =
      Logger.getLogger(JavaagentDistributionAccessCustomizerProvider.class.getName());

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .addHandler(
              new DeserializationProblemHandler() {
                @Override
                public boolean handleUnknownProperty(
                    DeserializationContext ctxt,
                    JsonParser p,
                    JsonDeserializer<?> deserializer,
                    Object beanOrClass,
                    String propertyName)
                    throws IOException {
                  logger.warning("Unknown distribution.javaagent property: " + propertyName);
                  p.skipChildren();
                  return true;
                }
              });

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          AgentDistributionConfig.set(parseConfig(model.getDistribution()));
          return model;
        });
  }

  private static AgentDistributionConfig parseConfig(@Nullable DistributionModel distribution) {
    if (distribution != null) {
      DistributionPropertyModel javaagent = distribution.getAdditionalProperties().get("javaagent");
      if (javaagent != null) {
        try {
          return MAPPER.convertValue(javaagent, AgentDistributionConfig.class);
        } catch (IllegalArgumentException e) {
          logger.log(WARNING, "Failed to parse distribution.javaagent configuration", e);
        }
      }
    }
    return AgentDistributionConfig.create();
  }
}
