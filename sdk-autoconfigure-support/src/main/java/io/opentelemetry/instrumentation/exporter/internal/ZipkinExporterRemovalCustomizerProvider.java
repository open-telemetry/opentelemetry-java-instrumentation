/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.exporter.internal;

import static java.util.Collections.emptyMap;

import io.opentelemetry.instrumentation.config.internal.DeclarativeConfigV3Preview;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.util.Map;

/**
 * Fails autoconfiguration when the Zipkin span exporter is configured while the v3 preview is
 * enabled, since Zipkin support will be removed in 3.0.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// to be removed for 3.0.0, when the zipkin exporter dependency itself is dropped
public class ZipkinExporterRemovalCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addPropertiesCustomizer(
        ZipkinExporterRemovalCustomizerProvider::customize);
  }

  static Map<String, String> customize(ConfigProperties config) {
    if (!config.getBoolean(DeclarativeConfigV3Preview.V3_PREVIEW_PROPERTY, false)) {
      return emptyMap();
    }
    for (String exporter : config.getList("otel.traces.exporter")) {
      if (ZipkinExporterRemoval.EXPORTER_NAME.equalsIgnoreCase(exporter.trim())) {
        throw new ConfigurationException(ZipkinExporterRemoval.ERROR_MESSAGE);
      }
    }
    return emptyMap();
  }

  @Override
  public int order() {
    // make sure it runs AFTER all the user-provided customizers
    return Integer.MAX_VALUE;
  }
}
