/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal;

import java.util.Arrays;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExporterUtil {

  private ExporterUtil() {}

  public static boolean isExporterEnabled(
      Environment environment,
      @Nullable String oldAllKey,
      String oldKey,
      String exportersKey,
      String wantExporter,
      boolean defaultValue) {

    String exporter = environment.getProperty(exportersKey);
    if (exporter != null) {
      return Arrays.asList(exporter.split(",")).contains(wantExporter);
    }

    String old = environment.getProperty(oldKey);
    if (old != null) {
      return "true".equals(old);
    }
    if (oldAllKey != null) {
      String oldAll = environment.getProperty(oldAllKey);
      if (oldAll != null) {
        return "true".equals(oldAll);
      }
    }
    return defaultValue;
  }
}
