/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.datasource;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import javax.sql.DataSource;
import org.checkerframework.checker.nullness.qual.Nullable;

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
  protected @Nullable String filePath(DataSource dataSource) {
    return null;
  }

  @Override
  protected @Nullable Long lineNumber(DataSource dataSource) {
    return null;
  }
}
