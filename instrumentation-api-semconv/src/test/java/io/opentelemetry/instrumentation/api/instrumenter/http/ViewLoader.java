package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.sdk.extension.incubator.metric.viewconfig.ViewConfig;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;

public class ViewLoader {
  private ViewLoader() {
  }

  public static void loadViews(SdkMeterProviderBuilder builder, String filename) {
    ViewConfig.registerViews(builder, resourceFileInputStream(filename));
  }

  private static InputStream resourceFileInputStream(String resourceFileName) {
    URL resourceUrl = ViewLoader.class.getResource("/" + resourceFileName);
    if (resourceUrl == null) {
      throw new IllegalStateException("Could not find resource file: " + resourceFileName);
    }
    String path = resourceUrl.getFile();
    try {
      return new FileInputStream(path);
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("File not found: " + path, e);
    }
  }
}
