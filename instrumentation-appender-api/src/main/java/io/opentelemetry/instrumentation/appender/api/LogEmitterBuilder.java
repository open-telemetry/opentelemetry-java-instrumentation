/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

/** Builder class for creating {@link LogEmitter} instances. */
public interface LogEmitterBuilder {

  /**
   * Assign an OpenTelemetry schema URL to the resulting {@link LogEmitter}.
   *
   * @param schemaUrl the URL of the OpenTelemetry schema being used by this instrumentation library
   * @return this
   */
  LogEmitterBuilder setSchemaUrl(String schemaUrl);

  /**
   * Assign a version to the instrumentation library that is using the resulting {@link LogEmitter}.
   *
   * @param instrumentationVersion the version of the instrumentation library
   * @return this
   */
  LogEmitterBuilder setInstrumentationVersion(String instrumentationVersion);

  /**
   * Gets or creates a {@link LogEmitter} instance.
   *
   * @return a log emitter instance configured with the provided options
   */
  LogEmitter build();
}
