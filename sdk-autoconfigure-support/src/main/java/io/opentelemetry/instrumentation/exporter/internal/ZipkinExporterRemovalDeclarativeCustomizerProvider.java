/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.exporter.internal;

import io.opentelemetry.instrumentation.config.internal.DeclarativeConfigV3Preview;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.BatchSpanProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SimpleSpanProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SpanExporterModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SpanProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.TracerProviderModel;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Fails declarative configuration when the Zipkin span exporter is configured while the v3 preview
 * is enabled, since Zipkin support will be removed in 3.0.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// to be removed for 3.0.0, when the zipkin exporter dependency itself is dropped
public class ZipkinExporterRemovalDeclarativeCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          checkZipkinExporter(model);
          return model;
        });
  }

  @Override
  public int order() {
    // make sure it runs AFTER all the user-provided customizers, so that the final model is
    // validated
    return Integer.MAX_VALUE;
  }

  static void checkZipkinExporter(OpenTelemetryConfigurationModel model) {
    if (!DeclarativeConfigV3Preview.isEnabled(model)) {
      return;
    }
    TracerProviderModel tracerProvider = model.getTracerProvider();
    if (tracerProvider == null) {
      return;
    }
    List<SpanProcessorModel> processors = tracerProvider.getProcessors();
    if (processors == null) {
      return;
    }
    for (SpanProcessorModel processor : processors) {
      BatchSpanProcessorModel batch = processor.getBatch();
      if (batch != null && isZipkin(batch.getExporter())) {
        throw new ConfigurationException(ZipkinExporterRemoval.ERROR_MESSAGE);
      }
      SimpleSpanProcessorModel simple = processor.getSimple();
      if (simple != null && isZipkin(simple.getExporter())) {
        throw new ConfigurationException(ZipkinExporterRemoval.ERROR_MESSAGE);
      }
    }
  }

  private static boolean isZipkin(@Nullable SpanExporterModel exporter) {
    return exporter != null
        && exporter.getAdditionalProperties().containsKey(ZipkinExporterRemoval.EXPORTER_NAME);
  }
}
