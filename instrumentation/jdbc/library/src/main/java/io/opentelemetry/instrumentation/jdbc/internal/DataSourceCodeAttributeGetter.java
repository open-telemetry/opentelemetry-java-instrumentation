/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;
import javax.sql.DataSource;

enum DataSourceCodeAttributeGetter implements CodeAttributeGetter<DataSource> {
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
