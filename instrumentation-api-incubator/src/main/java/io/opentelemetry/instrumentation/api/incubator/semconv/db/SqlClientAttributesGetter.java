/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
public interface SqlClientAttributesGetter<REQUEST, RESPONSE>
    extends DbClientCommonAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Get the raw SQL query texts. The values returned by this method is later sanitized by the
   * {@link SqlClientAttributesExtractor} before being set as span attribute.
   *
   * <p>If {@code request} is not a batch query, then this method should return a collection with a
   * single element.
   */
  Collection<String> getRawQueryTexts(REQUEST request);

  // TODO: make this required to implement
  default Long getBatchSize(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  default Map<String, String> getQueryParameters(REQUEST request) {
    return Collections.emptyMap();
  }
}
