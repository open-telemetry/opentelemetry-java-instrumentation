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

/**
 * Adds {@code instrumentation/development.java.foo.customizer_key} at SDK-build time, so tests
 * can verify that the {@code configProvider} bean reflects customizations applied to the
 * declarative model rather than the raw, pre-customization model.
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
    ExperimentalInstrumentationModel instrumentation = model.getInstrumentationDevelopment();
    if (instrumentation == null) {
      instrumentation = new ExperimentalInstrumentationModel();
      model.withInstrumentationDevelopment(instrumentation);
    }
    ExperimentalLanguageSpecificInstrumentationModel java = instrumentation.getJava();
    if (java == null) {
      java = new ExperimentalLanguageSpecificInstrumentationModel();
      instrumentation.withJava(java);
    }
    ExperimentalLanguageSpecificInstrumentationPropertyModel foo =
        java.getAdditionalProperties().get("foo");
    if (foo == null) {
      foo = new ExperimentalLanguageSpecificInstrumentationPropertyModel();
      java.withAdditionalProperty("foo", foo);
    }
    foo.withAdditionalProperty(CUSTOMIZER_KEY, CUSTOMIZED_VALUE);
    return model;
  }
}
