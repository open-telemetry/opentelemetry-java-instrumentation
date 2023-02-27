/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import javax.sql.DataSource;

final class DataSourceCodeAttributesGetter implements CodeAttributesGetter<DataSource> {

  @Override
  public Class<?> getCodeClass(DataSource dataSource) {
    return dataSource.getClass();
  }

  @Override
  public String getMethodName(DataSource dataSource) {
    return "getConnection";
  }
}
