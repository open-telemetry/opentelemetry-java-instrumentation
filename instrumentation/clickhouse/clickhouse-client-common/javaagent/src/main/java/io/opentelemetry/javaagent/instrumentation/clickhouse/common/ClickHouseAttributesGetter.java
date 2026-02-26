/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

final class ClickHouseAttributesGetter
    implements SqlClientAttributesGetter<ClickHouseDbRequest, Void> {

  private final Function<Throwable, String> errorCodeExtractor;

  ClickHouseAttributesGetter(Function<Throwable, String> errorCodeExtractor) {
    this.errorCodeExtractor = errorCodeExtractor;
  }

  @Override
  public Collection<String> getRawQueryTexts(ClickHouseDbRequest request) {
    return singletonList(request.getSql());
  }

  @Override
  public String getDbSystemName(ClickHouseDbRequest request) {
    return DbSystemNameIncubatingValues.CLICKHOUSE;
  }

  @Nullable
  @Override
  public String getDbNamespace(ClickHouseDbRequest request) {
    String namespace = request.getNamespace();
    if (namespace == null || namespace.isEmpty()) {
      return null;
    }
    return namespace;
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(@Nullable Void response, @Nullable Throwable error) {
    return errorCodeExtractor.apply(error);
  }

  @Override
  public String getServerAddress(ClickHouseDbRequest request) {
    return request.getHost();
  }

  @Override
  public Integer getServerPort(ClickHouseDbRequest request) {
    return request.getPort();
  }
}
