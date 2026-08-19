/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalGeneralInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpClientInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpServerInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalSanitizationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalUrlSanitizationModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utility that applies {@link DefaultInstrumentationConfig} defaults to the declarative model. */
final class DefaultInstrumentationConfigApplier {

  /**
   * Applies defaults to the declarative configuration model under {@code
   * instrumentation/development.java}. Existing values in the model take precedence; defaults are
   * only set for properties not already present.
   */
  @CanIgnoreReturnValue
  public static OpenTelemetryConfigurationModel applyToModel(
      DefaultInstrumentationConfig defaults, OpenTelemetryConfigurationModel model) {
    if (defaults.getDefaults().isEmpty()) {
      return model;
    }

    ExperimentalInstrumentationModel instrumentation = model.getInstrumentationDevelopment();
    if (instrumentation == null) {
      instrumentation = new ExperimentalInstrumentationModel();
      model.withInstrumentationDevelopment(instrumentation);
    }
    for (Map.Entry<String, Object> entry : defaults.getDefaults().entrySet()) {
      String declarativePath = entry.getKey();
      if (declarativePath.startsWith("general.")) {
        applyGeneralDefault(
            instrumentation, declarativePath.substring("general.".length()), entry.getValue());
      } else if (declarativePath.startsWith("java.")) {
        applyJavaDefault(
            instrumentation, declarativePath.substring("java.".length()), entry.getValue());
      } else {
        throw new IllegalStateException(
            "unexpected instrumentation default path: " + declarativePath);
      }
    }

    return model;
  }

  private static void applyJavaDefault(
      ExperimentalInstrumentationModel instrumentation, String declarativePath, Object value) {
    ExperimentalLanguageSpecificInstrumentationModel java = instrumentation.getJava();
    if (java == null) {
      java = new ExperimentalLanguageSpecificInstrumentationModel();
      instrumentation.withJava(java);
    }
    applyDefault(java.getAdditionalProperties(), declarativePath, value);
  }

  private static void applyGeneralDefault(
      ExperimentalInstrumentationModel instrumentation, String declarativePath, Object value) {
    ExperimentalGeneralInstrumentationModel general = instrumentation.getGeneral();
    if (general == null) {
      general = new ExperimentalGeneralInstrumentationModel();
      instrumentation.withGeneral(general);
    }

    switch (declarativePath) {
      case "http.client.request_captured_headers":
        ExperimentalHttpClientInstrumentationModel client = getOrCreateHttpClient(general);
        if (client.getRequestCapturedHeaders() == null) {
          client.withRequestCapturedHeaders(asStringList(declarativePath, value));
        }
        return;
      case "http.client.response_captured_headers":
        client = getOrCreateHttpClient(general);
        if (client.getResponseCapturedHeaders() == null) {
          client.withResponseCapturedHeaders(asStringList(declarativePath, value));
        }
        return;
      case "http.server.request_captured_headers":
        ExperimentalHttpServerInstrumentationModel server = getOrCreateHttpServer(general);
        if (server.getRequestCapturedHeaders() == null) {
          server.withRequestCapturedHeaders(asStringList(declarativePath, value));
        }
        return;
      case "http.server.response_captured_headers":
        server = getOrCreateHttpServer(general);
        if (server.getResponseCapturedHeaders() == null) {
          server.withResponseCapturedHeaders(asStringList(declarativePath, value));
        }
        return;
      case "sanitization.url.sensitive_query_parameters/development":
      case "sanitization.url.sensitive_query_parameters":
        ExperimentalUrlSanitizationModel url = getOrCreateUrlSanitization(general);
        if (url.getSensitiveQueryParameters() == null) {
          url.withSensitiveQueryParameters(asStringList(declarativePath, value));
        }
        return;
      case "semconv_stability.opt_in":
      case "stability_opt_in_list":
        if (general.getStabilityOptInList() == null) {
          general.withStabilityOptInList(asString(declarativePath, value));
        }
        return;
      default:
        throw new IllegalArgumentException(
            "unsupported general instrumentation default: " + declarativePath);
    }
  }

  private static ExperimentalHttpClientInstrumentationModel getOrCreateHttpClient(
      ExperimentalGeneralInstrumentationModel general) {
    ExperimentalHttpInstrumentationModel http = getOrCreateHttp(general);
    ExperimentalHttpClientInstrumentationModel client = http.getClient();
    if (client == null) {
      client = new ExperimentalHttpClientInstrumentationModel();
      http.withClient(client);
    }
    return client;
  }

  private static ExperimentalHttpServerInstrumentationModel getOrCreateHttpServer(
      ExperimentalGeneralInstrumentationModel general) {
    ExperimentalHttpInstrumentationModel http = getOrCreateHttp(general);
    ExperimentalHttpServerInstrumentationModel server = http.getServer();
    if (server == null) {
      server = new ExperimentalHttpServerInstrumentationModel();
      http.withServer(server);
    }
    return server;
  }

  private static ExperimentalHttpInstrumentationModel getOrCreateHttp(
      ExperimentalGeneralInstrumentationModel general) {
    ExperimentalHttpInstrumentationModel http = general.getHttp();
    if (http == null) {
      http = new ExperimentalHttpInstrumentationModel();
      general.withHttp(http);
    }
    return http;
  }

  private static ExperimentalUrlSanitizationModel getOrCreateUrlSanitization(
      ExperimentalGeneralInstrumentationModel general) {
    ExperimentalSanitizationModel sanitization = general.getSanitization();
    if (sanitization == null) {
      sanitization = new ExperimentalSanitizationModel();
      general.withSanitization(sanitization);
    }
    ExperimentalUrlSanitizationModel url = sanitization.getUrl();
    if (url == null) {
      url = new ExperimentalUrlSanitizationModel();
      sanitization.withUrl(url);
    }
    return url;
  }

  private static String asString(String path, Object value) {
    if (!(value instanceof String)) {
      throw new IllegalArgumentException(
          "general instrumentation default must be a string: " + path);
    }
    return (String) value;
  }

  private static List<String> asStringList(String path, Object value) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(
          "general instrumentation default must be a string list: " + path);
    }
    List<?> values = (List<?>) value;
    List<String> strings = new ArrayList<>(values.size());
    for (Object element : values) {
      if (!(element instanceof String)) {
        throw new IllegalArgumentException(
            "general instrumentation default must be a string list: " + path);
      }
      strings.add((String) element);
    }
    return strings;
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
    target.putIfAbsent(segments[segments.length - 1], value);
  }

  private DefaultInstrumentationConfigApplier() {}
}
