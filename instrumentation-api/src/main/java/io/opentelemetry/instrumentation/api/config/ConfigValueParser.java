/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

@FunctionalInterface
interface ConfigValueParser<T> {
  T parse(String propertyName, String rawValue) throws ConfigParsingException;
}
