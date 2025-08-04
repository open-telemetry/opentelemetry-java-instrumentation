/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;

public final class SdkAutoconfigureAccess {
  private SdkAutoconfigureAccess() {}

  public static Resource getResource(AutoConfiguredOpenTelemetrySdk sdk) {
    return sdk.getResource();
  }

  public static AutoConfiguredOpenTelemetrySdk create(
      OpenTelemetrySdk sdk, Resource resource, ConfigProperties config, Object configProvider) {
    return AutoConfiguredOpenTelemetrySdk.create(sdk, resource, config, configProvider);
  }
}
