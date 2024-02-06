/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal;

import java.util.Arrays;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExporterConfigEvaluator {

  private ExporterConfigEvaluator() {}

  public static boolean isExporterEnabled(
      Environment environment, String exportersKey, String wantExporter, boolean defaultValue) {

    String exporter = environment.getProperty(exportersKey);
    if (exporter != null) {
      return Arrays.asList(exporter.split(",")).contains(wantExporter);
    }

    return defaultValue;
  }
}
