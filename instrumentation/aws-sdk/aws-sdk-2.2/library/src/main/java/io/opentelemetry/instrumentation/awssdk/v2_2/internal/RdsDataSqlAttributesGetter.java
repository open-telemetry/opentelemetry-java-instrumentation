/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

class RdsDataSqlAttributesGetter
    implements SqlClientAttributesGetter<ExecutionAttributes, Response> {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  @Override
  public String getDbSystemName(ExecutionAttributes request) {
    return OTHER_SQL;
  }

  @Override
  @Nullable
  public String getDbNamespace(ExecutionAttributes request) {
    return sdkRequest(request).getValueForField("database", String.class).orElse(null);
  }

  @Override
  public SqlDialect getSqlDialect(ExecutionAttributes request) {
    return DOUBLE_QUOTES_ARE_STRING_LITERALS;
  }

  @Override
  public Collection<String> getRawQueryTexts(ExecutionAttributes request) {
    String sql = sdkRequest(request).getValueForField("sql", String.class).orElse(null);
    return sql == null ? emptyList() : singletonList(sql);
  }

  @Override
  public boolean isParameterizedQuery(ExecutionAttributes request, int queryIndex) {
    SdkRequest sdkRequest = sdkRequest(request);
    List<?> parameters = getListField(sdkRequest, "parameters");
    if (!parameters.isEmpty()) {
      return true;
    }
    for (Object parameterSet : getListField(sdkRequest, "parameterSets")) {
      if (parameterSet instanceof Collection && !((Collection<?>) parameterSet).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(ExecutionAttributes request) {
    Object parameterSets =
        sdkRequest(request).getValueForField("parameterSets", Object.class).orElse(null);
    if (parameterSets instanceof List) {
      return (long) ((List<?>) parameterSets).size();
    }
    return null;
  }

  private static SdkRequest sdkRequest(ExecutionAttributes request) {
    return request.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
  }

  private static List<?> getListField(SdkRequest request, String fieldName) {
    Object value = request.getValueForField(fieldName, Object.class).orElse(null);
    return value instanceof List ? (List<?>) value : emptyList();
  }
}
