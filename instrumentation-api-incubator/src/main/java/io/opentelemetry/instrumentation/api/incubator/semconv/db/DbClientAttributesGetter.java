/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * An interface for getting database client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link DbClientAttributesExtractor} to obtain the
 * various database client attributes in a type-generic way.
 *
 * <p>If an attribute is not available in this library, it is appropriate to return {@code null}
 * from the attribute methods, but implement as many as possible for best compliance with the
 * OpenTelemetry specification.
 */
public interface DbClientAttributesGetter<REQUEST, RESPONSE>
    extends NetworkAttributesGetter<REQUEST, RESPONSE>, ServerAttributesGetter<REQUEST> {

  @Nullable
  String getDbQueryText(REQUEST request);

  // TODO: make this required to implement
  @Nullable
  default String getDbQuerySummary(REQUEST request) {
    return null;
  }

  @Nullable
  String getDbOperationName(REQUEST request);

  // TODO: make this required to implement
  String getDbSystemName(REQUEST request);

  @Nullable
  String getDbNamespace(REQUEST request);

  /**
   * Returns the database name for old semantic conventions. This is only used for the old {@code
   * db.name} attribute. Override this if the old semconv value differs from the new {@code
   * db.namespace} value.
   *
   * @deprecated This method is only for backward compatibility with old semantic conventions.
   */
  @Deprecated // will be removed in 3.0.0
  @Nullable
  default String getDbName(REQUEST request) {
    return getDbNamespace(request);
  }

  /**
   * Returns the database user name. This is only used for old semantic conventions.
   *
   * @deprecated There is no replacement at this time.
   */
  @Deprecated
  @Nullable
  default String getUser(REQUEST request) {
    return null;
  }

  /**
   * Returns the database connection string. This is only used for old semantic conventions.
   *
   * @deprecated There is no replacement at this time.
   */
  @Deprecated
  @Nullable
  default String getConnectionString(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  @Nullable
  default String getDbResponseStatusCode(@Nullable RESPONSE response, @Nullable Throwable error) {
    return null;
  }

  // TODO: make this required to implement
  @Nullable
  default Long getDbOperationBatchSize(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  default Map<String, String> getDbQueryParameters(REQUEST request) {
    return Collections.emptyMap();
  }
}
