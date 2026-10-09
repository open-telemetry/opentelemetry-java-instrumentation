/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.util.Collection;
import javax.annotation.Nullable;

final class GeodeSqlAttributesGetter implements SqlClientAttributesGetter<GeodeRequest, Void> {

  private final GeodeDbAttributesGetter dbGetter = new GeodeDbAttributesGetter();

  @Override
  public SqlDialect getSqlDialect(GeodeRequest request) {
    // OQL string literals are delimited by single quotation marks.
    return DOUBLE_QUOTES_ARE_IDENTIFIERS;
  }

  @Override
  public Collection<String> getRawQueryTexts(GeodeRequest request) {
    String queryText = request.getQueryText();
    return queryText == null ? emptyList() : singletonList(queryText);
  }

  @Override
  public String getDbSystemName(GeodeRequest request) {
    return dbGetter.getDbSystemName(request);
  }

  @Override
  @Nullable
  public String getDbNamespace(GeodeRequest request) {
    return dbGetter.getDbNamespace(request);
  }

  @Override
  @Nullable
  public String getServerAddress(GeodeRequest request) {
    return dbGetter.getServerAddress(request);
  }

  @Override
  @Nullable
  public Integer getServerPort(GeodeRequest request) {
    return dbGetter.getServerPort(request);
  }
}
