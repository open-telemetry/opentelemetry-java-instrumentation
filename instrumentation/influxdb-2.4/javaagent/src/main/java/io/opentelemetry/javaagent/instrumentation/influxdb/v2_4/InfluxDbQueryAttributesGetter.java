/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.util.Collection;
import javax.annotation.Nullable;

final class InfluxDbQueryAttributesGetter
    implements SqlClientAttributesGetter<InfluxDbQuery, Void> {

  @Override
  public Collection<String> getRawQueryTexts(InfluxDbQuery request) {
    String query = request.getQuery();
    if (query == null) {
      return emptyList();
    }
    return singletonList(query);
  }

  @Override
  public SqlDialect getSqlDialect(InfluxDbQuery request) {
    // "String literals must be surrounded by single quotes."
    // https://docs.influxdata.com/influxdb/v2/reference/syntax/influxql/spec/#strings
    return DOUBLE_QUOTES_ARE_IDENTIFIERS;
  }

  @Override
  public String getDbSystemName(InfluxDbQuery request) {
    return "influxdb";
  }

  @Nullable
  @Override
  public String getDbNamespace(InfluxDbQuery request) {
    return request.getNamespace();
  }

  @Override
  public String getServerAddress(InfluxDbQuery request) {
    return request.getHost();
  }

  @Override
  public Integer getServerPort(InfluxDbQuery request) {
    return request.getPort();
  }
}
