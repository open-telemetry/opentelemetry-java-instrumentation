/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class ResourceProviderRegistrationTest {

  @Test
  void resourceProvidersAreRegistered() {
    assertThat(ServiceLoader.load(ResourceProvider.class))
        .filteredOn(
            provider ->
                provider
                    .getClass()
                    .getPackage()
                    .getName()
                    .equals("io.opentelemetry.javaagent.tooling.resources"))
        .extracting(provider -> provider.getClass().getSimpleName())
        .containsExactlyInAnyOrder("DistroResourceProvider");
  }
}
