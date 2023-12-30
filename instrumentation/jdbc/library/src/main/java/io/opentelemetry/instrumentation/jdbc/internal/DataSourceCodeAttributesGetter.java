/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.sql.DataSource;

enum DataSourceCodeAttributesGetter implements CodeAttributesGetter<DataSource> {
  INSTANCE;

  @Override
  public Class<?> getCodeClass(DataSource dataSource) {
    return dataSource.getClass();
  }

  @Override
  public String getMethodName(DataSource dataSource) {
    return "getConnection";
  }
}
