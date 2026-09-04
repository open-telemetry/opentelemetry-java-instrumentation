/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

class ClickHouseAttributesGetter implements SqlClientAttributesGetter<ClickHouseDbRequest, Void> {

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

  @Override
  public SqlDialect getSqlDialect(ClickHouseDbRequest request) {
    // "String literals must be enclosed in single quotes.
    // Double quotes are not supported."
    // https://clickhouse.com/docs/en/sql-reference/syntax#string
    return DOUBLE_QUOTES_ARE_IDENTIFIERS;
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
  public String getErrorType(
      ClickHouseDbRequest request, @Nullable Void response, @Nullable Throwable error) {
    return errorCodeExtractor.apply(error);
  }

  @Nullable
  @Override
  public String getServerAddress(ClickHouseDbRequest request) {
    if (emitStableDatabaseSemconv()) {
      DbServerTarget serverTarget = request.getServerTarget();
      return serverTarget == null ? null : serverTarget.getAddress();
    }
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(ClickHouseDbRequest request) {
    if (emitStableDatabaseSemconv()) {
      DbServerTarget serverTarget = request.getServerTarget();
      return serverTarget == null ? null : serverTarget.getPort();
    }
    return request.getPort();
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(ClickHouseDbRequest request, @Nullable Void response) {
    return emitStableDatabaseSemconv() ? request.getPeerAddress() : null;
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(ClickHouseDbRequest request, @Nullable Void response) {
    return emitStableDatabaseSemconv() ? request.getPeerPort() : null;
  }
}
