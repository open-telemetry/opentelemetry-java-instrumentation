/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.boot.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class ResourceProviderRegistrationTest {

  @Test
  void serviceNameDetectorIsRegistered() {
    assertThat(ServiceLoader.load(ResourceProvider.class))
        .filteredOn(provider -> provider instanceof SpringBootServiceNameDetector)
        .singleElement()
        .satisfies(
            provider -> {
              assertThat(provider).isExactlyInstanceOf(SpringBootServiceNameDetector.class);
              assertThat(provider.order()).isEqualTo(100);
              assertThat(provider.createResource(mock(ConfigProperties.class)))
                  .isEqualTo(Resource.builder().put(SERVICE_NAME, "dog-store").build());
            });
  }

  @Test
  void serviceVersionDetectorIsRegistered() {
    assertThat(ServiceLoader.load(ResourceProvider.class))
        .filteredOn(provider -> provider instanceof SpringBootServiceVersionDetector)
        .singleElement()
        .satisfies(
            provider -> {
              assertThat(provider).isExactlyInstanceOf(SpringBootServiceVersionDetector.class);
              assertThat(provider.order()).isZero();
              assertThat(provider.createResource(mock(ConfigProperties.class)))
                  .isEqualTo(Resource.builder().put(SERVICE_VERSION, "0.0.2").build());
            });
  }

  @Test
  void springResourceComponentProviderIsRegistered() {
    assertThat(ServiceLoader.load(ComponentProvider.class))
        .filteredOn(
            provider -> provider.getType() == Resource.class && provider.getName().equals("spring"))
        .singleElement()
        .satisfies(
            provider -> {
              assertThat(provider).isExactlyInstanceOf(SpringResourceComponentProvider.class);
              assertThat(provider.create(DeclarativeConfigProperties.empty()))
                  .isEqualTo(
                      Resource.builder()
                          .put(SERVICE_NAME, "dog-store")
                          .put(SERVICE_VERSION, "0.0.2")
                          .build());
            });
  }
}
