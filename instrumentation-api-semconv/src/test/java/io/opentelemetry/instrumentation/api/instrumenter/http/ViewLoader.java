/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.sdk.extension.incubator.metric.viewconfig.ViewConfig;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import java.io.InputStream;

public class ViewLoader {
  private ViewLoader() {}

  public static void loadViews(SdkMeterProviderBuilder builder, String filename) {
    ViewConfig.registerViews(builder, resourceFileInputStream(filename));
  }

  private static InputStream resourceFileInputStream(String resourceFileName) {
    return ViewLoader.class.getResourceAsStream("/" + resourceFileName);
  }
}
