/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalGeneralInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.OpenTelemetryConfigurationModelAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utility that applies {@link DefaultInstrumentationConfig} defaults to the declarative model. */
final class DefaultInstrumentationConfigApplier {

  private static final String COMMON_SERVICE_PEER_MAPPING = "common.service_peer_mapping";
  private static final String COMMON_SERVER_SERVICE_MAPPING = "common.server_service_mapping";

  /**
   * Applies defaults to the declarative configuration model under {@code
   * instrumentation/development}. Existing values in the model take precedence; defaults are only
   * set for properties not already present.
   */
  @CanIgnoreReturnValue
  static OpenTelemetryConfigurationModel applyToModel(
      DefaultInstrumentationConfig defaults, OpenTelemetryConfigurationModel model) {
    if (defaults.getDefaults().isEmpty() && defaults.getGeneralDefaults() == null) {
      return model;
    }

    ExperimentalInstrumentationModel instrumentation =
        OpenTelemetryConfigurationModelAccessor.getInstrumentation(model);
    boolean newInstrumentation = instrumentation == null;
    if (instrumentation == null) {
      instrumentation = new ExperimentalInstrumentationModel();
    }
    boolean userServiceMappingConfigured = hasUserServiceMapping(instrumentation);
    boolean canonicalServiceMappingDefault =
        defaults.getDefaults().containsKey(COMMON_SERVER_SERVICE_MAPPING);
    for (Map.Entry<String, Object> entry : defaults.getDefaults().entrySet()) {
      if (isServiceMappingPath(entry.getKey())) {
        if (userServiceMappingConfigured) {
          continue;
        }
        if (canonicalServiceMappingDefault && entry.getKey().equals(COMMON_SERVICE_PEER_MAPPING)) {
          continue;
        }
      }
      applyJavaDefault(instrumentation, entry.getKey(), entry.getValue());
    }
    ExperimentalGeneralInstrumentationModel generalDefaults = defaults.getGeneralDefaults();
    if (generalDefaults != null) {
      ExperimentalGeneralInstrumentationModel general = instrumentation.getGeneral();
      if (general == null) {
        general = new ExperimentalGeneralInstrumentationModel();
        instrumentation.setGeneral(general);
      }
      DeclarativeModelUtil.mergeDefaults(general, generalDefaults);
    }
    if (newInstrumentation) {
      OpenTelemetryConfigurationModelAccessor.setInstrumentation(model, instrumentation);
    }

    return model;
  }

  private static boolean hasUserServiceMapping(ExperimentalInstrumentationModel instrumentation) {
    ExperimentalLanguageSpecificInstrumentationModel java = instrumentation.getJava();
    if (java == null) {
      return false;
    }
    ExperimentalLanguageSpecificInstrumentationPropertyModel common =
        java.getAdditionalProperties().get("common");
    if (common == null) {
      return false;
    }
    Map<String, Object> commonProperties = common.getAdditionalProperties();
    return commonProperties.get("service_peer_mapping") != null
        || commonProperties.get("server_service_mapping") != null;
  }

  private static boolean isServiceMappingPath(String path) {
    return path.equals(COMMON_SERVICE_PEER_MAPPING) || path.equals(COMMON_SERVER_SERVICE_MAPPING);
  }

  private static void applyJavaDefault(
      ExperimentalInstrumentationModel instrumentation, String declarativePath, Object value) {
    ExperimentalLanguageSpecificInstrumentationModel java = instrumentation.getJava();
    if (java == null) {
      java = new ExperimentalLanguageSpecificInstrumentationModel();
      instrumentation.setJava(java);
    }
    applyDefault(java.getAdditionalProperties(), declarativePath, value);
  }

  private static void applyDefault(
      Map<String, ExperimentalLanguageSpecificInstrumentationPropertyModel> props,
      String declarativePath,
      Object value) {
    String[] segments = declarativePath.split("\\.");
    ExperimentalLanguageSpecificInstrumentationPropertyModel propertyModel =
        props.computeIfAbsent(
            segments[0], key -> new ExperimentalLanguageSpecificInstrumentationPropertyModel());
    Map<String, Object> target = propertyModel.getAdditionalProperties();
    for (int i = 1; i < segments.length - 1; i++) {
      Object child = target.get(segments[i]);
      if (child == null) {
        Map<String, Object> nested = new HashMap<>();
        target.put(segments[i], nested);
        target = nested;
        continue;
      }
      if (!(child instanceof Map)) {
        return;
      }
      // Nested defaults only create string-keyed maps, so this cast is safe here.
      @SuppressWarnings("unchecked")
      Map<String, Object> nested = (Map<String, Object>) child;
      target = nested;
    }
    target.putIfAbsent(segments[segments.length - 1], copyValue(value));
  }

  private static Object copyValue(Object value) {
    if (value instanceof List) {
      List<Object> copy = new ArrayList<>();
      for (Object item : (List<?>) value) {
        copy.add(copyValue(item));
      }
      return copy;
    }
    if (value instanceof Map) {
      Map<Object, Object> copy = new HashMap<>();
      ((Map<?, ?>) value).forEach((key, item) -> copy.put(key, copyValue(item)));
      return copy;
    }
    return value;
  }

  private DefaultInstrumentationConfigApplier() {}
}
