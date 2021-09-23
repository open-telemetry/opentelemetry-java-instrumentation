/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ResourceMapping {
  public static ResourceMapping of(String resourcePath, String containerPath) {
    return new AutoValue_ResourceMapping(resourcePath, containerPath);
  }

  public abstract String resourcePath();

  public abstract String containerPath();
}
