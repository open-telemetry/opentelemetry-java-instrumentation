/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.exporter.internal;

/**
 * Shared constants for the v3-preview removal of the Zipkin span exporter.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// to be removed for 3.0.0, when the zipkin exporter dependency itself is dropped
final class ZipkinExporterRemoval {

  static final String EXPORTER_NAME = "zipkin";

  static final String ERROR_MESSAGE =
      "The zipkin span exporter is not supported when "
          + "otel.instrumentation.common.v3-preview is enabled, because zipkin support will be "
          + "removed in 3.0. Use the otlp exporter instead.";

  private ZipkinExporterRemoval() {}
}
