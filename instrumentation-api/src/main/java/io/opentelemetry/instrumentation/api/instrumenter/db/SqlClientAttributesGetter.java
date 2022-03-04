/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import javax.annotation.Nullable;

/**
 * An interface for getting SQL database client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link SqlClientAttributesExtractor} to obtain the
 * various SQL database client attributes in a type-generic way.
 *
 * <p>If an attribute is not available in this library, it is appropriate to return {@code null}
 * from the attribute methods, but implement as many as possible for best compliance with the
 * OpenTelemetry specification.
 */
public interface SqlClientAttributesGetter<REQUEST>
    extends DbClientCommonAttributesGetter<REQUEST> {

  /**
   * Get the raw SQL statement. The value returned by this method is later sanitized by the {@link
   * SqlClientAttributesExtractor} before being set as span attribute.
   */
  @Nullable
  String rawStatement(REQUEST request);
}
