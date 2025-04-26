/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public record ConfigurationOption(
    String name, String description, @JsonProperty("default") String defaultValue) {}
