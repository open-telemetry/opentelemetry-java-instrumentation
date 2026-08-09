/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.internal;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationPropertyModel;

/**
 * Reads the {@code instrumentation/development.java.common.v3_preview} flag from the declarative
 * configuration model.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// to be removed for 3.0.0
public final class DeclarativeConfigV3Preview {

  /** The property name used to enable the v3 preview when configuring via config properties. */
  public static final String V3_PREVIEW_PROPERTY = "otel.instrumentation.common.v3-preview";

  public static boolean isEnabled(OpenTelemetryConfigurationModel model) {
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

  private DeclarativeConfigV3Preview() {}
}
