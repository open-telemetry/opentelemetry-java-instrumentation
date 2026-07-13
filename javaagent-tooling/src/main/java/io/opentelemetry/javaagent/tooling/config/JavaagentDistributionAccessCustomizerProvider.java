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
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.DistributionModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.DistributionPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.ExperimentalLanguageSpecificInstrumentationPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
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

  private static final ObjectMapper mapper =
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
          boolean isV3Preview = isV3Preview(model);
          AgentDistributionConfig distributionConfig =
              parseConfig(model.getDistribution(), isV3Preview);
          AgentDistributionConfig.set(distributionConfig);
          return model;
        });
  }

  // to be removed for 3.0.0
  private static boolean isV3Preview(OpenTelemetryConfigurationModel model) {
    ExperimentalInstrumentationModel instrumentationDevelopment =
        model.getInstrumentationDevelopment();
    if (instrumentationDevelopment == null) {
      return false;
    }
    ExperimentalLanguageSpecificInstrumentationModel java = instrumentationDevelopment.getJava();
    if (java == null) {
      return false;
    }
    ExperimentalLanguageSpecificInstrumentationPropertyModel common =
        java.getAdditionalProperties().get("common");
    if (common == null) {
      return false;
    }
    return Boolean.TRUE.equals(common.getAdditionalProperties().get("v3_preview"));
  }

  private static AgentDistributionConfig parseConfig(
      @Nullable DistributionModel distribution, boolean v3Preview) {

    // to be removed for 3.0.0
    // defaults 'distribution.javaagent.indy/development' to 'true' for v3 preview if unset
    if (v3Preview) {
      // creating distribution.javaagent is required to add indy/development to it
      DistributionPropertyModel javaagent;
      if (distribution == null) {
        distribution = new DistributionModel();
      }
      javaagent = distribution.getAdditionalProperties().get("javaagent");
      if (javaagent == null) {
        javaagent = new DistributionPropertyModel();
        distribution.withAdditionalProperty("javaagent", javaagent);
      }
      // when v3 preview is enabled, force indy enabled
      javaagent.withAdditionalProperty("indy/development", true);
    }

    if (distribution != null) {
      DistributionPropertyModel javaagent = distribution.getAdditionalProperties().get("javaagent");
      if (javaagent != null) {
        try {
          return mapper.convertValue(javaagent, AgentDistributionConfig.class);
        } catch (IllegalArgumentException e) {
          logger.log(WARNING, "Failed to parse distribution.javaagent configuration", e);
        }
      }
    }

    return AgentDistributionConfig.create();
  }
}
