/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.api.common.Attributes;

public final class SdkAutoconfigureAccess {
  public static Attributes getResourceAttributes(AutoConfiguredOpenTelemetrySdk sdk) {
    return sdk.getResource().getAttributes();
  }

  private SdkAutoconfigureAccess() {}
}
