/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.sdk.autoconfigure.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurableProvider;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class SpringComponentLoader implements ComponentLoader {
  private final ApplicationContext applicationContext;

  private final SpiHelper spiHelper =
      SpiHelper.create(SpringComponentLoader.class.getClassLoader());

  public SpringComponentLoader(ApplicationContext applicationContext) {
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

  @Override
  public <T extends Ordered> List<T> loadOrdered(Class<T> spiClass) {
    List<T> result = spiHelper.loadOrdered(spiClass);

    // don't use the Ordered annotation, because users have to implement the order method anyways
    List<T> beans =
        applicationContext.getBeanProvider(spiClass).stream()
            .sorted(Comparator.comparing(Ordered::order))
            .collect(Collectors.toList());

    // (user provided) beans have a higher priority, e.g. to replace a resource from a resource
    // provider
    result.addAll(beans);
    return result;
  }

  @Override
  public <T extends ConfigurableProvider> Map<String, T> loadConfigurableProviders(
      Class<T> spiClass) {
    Map<String, T> components = new HashMap<>();
    addComponents(components, spiHelper.load(spiClass));
    // beans have a higher priority than spi and overwrite values with the same name
    addComponents(
        components,
        applicationContext.getBeanProvider(spiClass).orderedStream().collect(Collectors.toList()));
    return components;
  }

  private static <T> void addComponents(Map<String, T> target, List<T> components) {
    for (T component : components) {
      String name = ((ConfigurableProvider) component).getName();
      target.put(name, component);
    }
  }
}
