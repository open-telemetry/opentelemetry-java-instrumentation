/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.sdk.autoconfigure.internal.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OpenTelemetrySdkComponentLoader implements ComponentLoader {
  private final ApplicationContext applicationContext;

  private final SpiHelper spiHelper =
      SpiHelper.create(OpenTelemetrySdkComponentLoader.class.getClassLoader());

  public OpenTelemetrySdkComponentLoader(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public <T> Iterable<T> load(Class<T> spiClass) {
    List<T> spi = spiHelper.load(spiClass);
    List<T> beans =
        applicationContext.getBeanProvider(spiClass).stream().collect(Collectors.toList());
    spi.addAll(beans);
    return spi;
  }
}
