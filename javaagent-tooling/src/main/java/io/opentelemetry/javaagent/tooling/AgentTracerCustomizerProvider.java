/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;

@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public class AgentTracerCustomizerProvider implements DeclarativeConfigurationCustomizerProvider {
  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          TracerProviderModel tracerProvider = model.getTracerProvider();
          if (tracerProvider == null) {
            return model;
          }
          //          InstrumentationModel instrumentationModel =
          // model.getInstrumentationDevelopment();
          //          ExperimentalLanguageSpecificInstrumentationModel java =
          // instrumentationModel.getJava();
          // todo how to get the "add_thread_details" from the config?
          // do we need a ConfigProvider?

          // todo also add logging like in AgentTracerProviderConfigurer

          tracerProvider
              .getProcessors()
              .add(new SpanProcessorModel().withAdditionalProperty("thread_details", null));
          return model;
        });
  }
}
