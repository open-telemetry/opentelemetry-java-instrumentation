/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.internal.InternalInstrumenterCustomizerProviderImpl;
import io.opentelemetry.instrumentation.api.incubator.thread.ThreadDetailsAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizerUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.EarlyConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@Conditional(OtelEnabled.class)
@Configuration
public class ThreadDetailsAutoConfiguration {

  public ThreadDetailsAutoConfiguration(Environment environment) {
    if (isThreadDetailsEnabled(environment)) {
      List<InternalInstrumenterCustomizerProvider> providers =
          new ArrayList<>(InternalInstrumenterCustomizerUtil.getInstrumenterCustomizerProviders());
      providers.add(
          new InternalInstrumenterCustomizerProviderImpl(
              customizer ->
                  customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>())));
      InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(providers);
    }
  }

  static boolean isThreadDetailsEnabled(Environment environment) {
    if (EarlyConfig.isDeclarativeConfig(environment)) {
      return environment.getProperty(
          "otel.distribution.spring_starter.thread_details_enabled", Boolean.class, false);
    }
    return environment.getProperty(
        "otel.instrumentation.common.thread-details.enabled", Boolean.class, false);
  }
}
