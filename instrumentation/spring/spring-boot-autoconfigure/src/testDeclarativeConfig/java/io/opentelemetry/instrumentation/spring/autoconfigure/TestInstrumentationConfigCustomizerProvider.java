/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.OpenTelemetryConfigurationModelAccessor;

/**
 * Adds {@code instrumentation/development.java.foo.customizer_key} at SDK-build time, so tests can
 * verify that the {@code configProvider} bean reflects customizations applied to the declarative
 * model rather than the raw, pre-customization model.
 */
public class TestInstrumentationConfigCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  static final String CUSTOMIZER_KEY = "customizer_key";
  static final String CUSTOMIZED_VALUE = "customized_by_model_customizer";

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(TestInstrumentationConfigCustomizerProvider::customizeModel);
  }

  private static OpenTelemetryConfigurationModel customizeModel(
      OpenTelemetryConfigurationModel model) {
    ExperimentalInstrumentationModel instrumentation =
        OpenTelemetryConfigurationModelAccessor.getInstrumentation(model);
    if (instrumentation == null) {
      instrumentation = new ExperimentalInstrumentationModel();
    }
    ExperimentalLanguageSpecificInstrumentationModel java = instrumentation.getJava();
    if (java == null) {
      java = new ExperimentalLanguageSpecificInstrumentationModel();
      instrumentation.setJava(java);
    }
    ExperimentalLanguageSpecificInstrumentationPropertyModel foo =
        java.getAdditionalProperties().get("foo");
    if (foo == null) {
      foo = new ExperimentalLanguageSpecificInstrumentationPropertyModel();
    }
    foo.setAdditionalProperty(CUSTOMIZER_KEY, CUSTOMIZED_VALUE);
    java.setAdditionalProperty("foo", foo);
    OpenTelemetryConfigurationModelAccessor.setInstrumentation(model, instrumentation);
    return model;
  }
}
