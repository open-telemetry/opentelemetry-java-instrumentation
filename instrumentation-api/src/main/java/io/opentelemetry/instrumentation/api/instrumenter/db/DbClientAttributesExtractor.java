/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/database.md">database
 * client attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link DbClientAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class DbClientAttributesExtractor<REQUEST, RESPONSE>
    extends DbCommonAttributesExtractor<REQUEST, RESPONSE, DbClientAttributesGetter<REQUEST>> {

  /** Creates the database client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> DbClientAttributesExtractor<REQUEST, RESPONSE> create(
      DbClientAttributesGetter<REQUEST> getter) {
    return new DbClientAttributesExtractor<>(getter);
  }

  DbClientAttributesExtractor(DbClientAttributesGetter<REQUEST> getter) {
    super(getter);
  }
}
