/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import javax.annotation.Nullable;
import javax.sql.DataSource;

final class DataSourceCodeAttributesExtractor extends CodeAttributesExtractor<DataSource, Void> {

  @Override
  protected Class<?> codeClass(DataSource dataSource) {
    return dataSource.getClass();
  }

  @Override
  protected String methodName(DataSource dataSource) {
    return "getConnection";
  }

  @Override
  @Nullable
  protected String filePath(DataSource dataSource) {
    return null;
  }

  @Override
  @Nullable
  protected Long lineNumber(DataSource dataSource) {
    return null;
  }
}
