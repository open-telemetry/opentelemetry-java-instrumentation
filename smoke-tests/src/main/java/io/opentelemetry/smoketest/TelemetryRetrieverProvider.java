/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

/**
 * Provides a {@link TelemetryRetriever} instance. Tests using {@link
 * SmokeTestInstrumentationExtension} must implement this interface.
 */
public interface TelemetryRetrieverProvider {
  TelemetryRetriever getTelemetryRetriever();
}
