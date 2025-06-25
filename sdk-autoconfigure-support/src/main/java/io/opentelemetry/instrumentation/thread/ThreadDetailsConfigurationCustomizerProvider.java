package io.opentelemetry.instrumentation.thread;

import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;

public class ThreadDetailsConfigurationCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {
  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          TracerProviderModel tracerProvider = model.getTracerProvider();
          if (tracerProvider == null) {
            return model;
          }
          tracerProvider
              .getProcessors()
              .add(new SpanProcessorModel().withAdditionalProperty("thread_details", null));
          return model;
        });
  }
}
