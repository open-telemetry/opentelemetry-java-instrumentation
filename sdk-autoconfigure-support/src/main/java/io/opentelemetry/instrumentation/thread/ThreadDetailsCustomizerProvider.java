/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;

public class ThreadDetailsCustomizerProvider implements DeclarativeConfigurationCustomizerProvider {
  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          DeclarativeConfigProperties properties =
              SdkConfigProvider.create(model).getInstrumentationConfig();
          if (properties == null) {
            properties = DeclarativeConfigProperties.empty();
          }
          DeclarativeConfigProperties java =
              properties.getStructured("java", DeclarativeConfigProperties.empty());
          if (!java.getBoolean("enabled", true)) {
            // todo extract this logic to a common place
            // todo should this be pulled out or to be reusable by spring?
            return model;
          }

          if (java.getStructured("thread_details", DeclarativeConfigProperties.empty())
              .getBoolean("enabled", true)) {
            TracerProviderModel tracerProvider = model.getTracerProvider();
            if (tracerProvider != null) {
              tracerProvider
                  .getProcessors()
                  .add(new SpanProcessorModel().withAdditionalProperty("thread_details", null));
            }
          }

          // todo also add logging like in AgentTracerProviderConfigurer

          return model;
        });
  }
}
