/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

class SemconvDomain {
  private final String configName;
  private final String flagKey;
  private final boolean hasStructuredConfig;
  private final SemconvMode defaultMode;
  private final Set<SemconvMode> supportedModes;

  static Builder builder(String configName) {
    return new Builder(configName);
  }

  private SemconvDomain(
      String configName,
      String flagKey,
      boolean hasStructuredConfig,
      SemconvMode defaultMode,
      Set<SemconvMode> supportedModes) {
    this.configName = configName;
    this.flagKey = flagKey;
    this.hasStructuredConfig = hasStructuredConfig;
    this.defaultMode = defaultMode;
    this.supportedModes = new HashSet<>(supportedModes);
  }

  String configName() {
    return configName;
  }

  String flagKey() {
    return flagKey;
  }

  boolean hasStructuredConfig() {
    return hasStructuredConfig;
  }

  SemconvMode defaultMode() {
    return defaultMode;
  }

  Set<SemconvMode> supportedModes() {
    return supportedModes;
  }

  static class Builder {
    private final String configName;
    private String flagKey;
    private boolean hasStructuredConfig = true;
    @Nullable private SemconvMode defaultMode;
    private final Set<SemconvMode> supportedModes = new HashSet<>();

    private Builder(String configName) {
      this.configName = configName;
      this.flagKey = configName;
    }

    @CanIgnoreReturnValue
    Builder flagKey(String flagKey) {
      this.flagKey = flagKey;
      return this;
    }

    @CanIgnoreReturnValue
    Builder withoutStructuredConfig() {
      hasStructuredConfig = false;
      return this;
    }

    @CanIgnoreReturnValue
    Builder defaultMode(SemconvMode defaultMode) {
      this.defaultMode = defaultMode;
      supportedModes.add(defaultMode);
      return this;
    }

    @CanIgnoreReturnValue
    Builder otherSupportedModes(SemconvMode... modes) {
      supportedModes.addAll(asList(modes));
      return this;
    }

    SemconvDomain build() {
      if (defaultMode == null) {
        throw new IllegalStateException("defaultMode is required");
      }
      return new SemconvDomain(
          configName, flagKey, hasStructuredConfig, defaultMode, supportedModes);
    }
  }
}
