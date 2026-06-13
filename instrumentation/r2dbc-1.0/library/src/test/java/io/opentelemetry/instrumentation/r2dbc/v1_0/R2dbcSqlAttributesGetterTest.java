/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.R2dbcSqlAttributesGetter;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class R2dbcSqlAttributesGetterTest {

  private final R2dbcSqlAttributesGetter getter = new R2dbcSqlAttributesGetter();

  @Test
  void rawQueryTextsForBatch() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(1)"))
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(2)"))
            .batchSize(2)
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);

    Collection<String> rawQueryTexts = getter.getRawQueryTexts(dbExecution);

    if (emitStableDatabaseSemconv()) {
      assertThat(rawQueryTexts)
          .containsExactly("INSERT INTO person VALUES(1)", "INSERT INTO person VALUES(2)");
    } else {
      assertThat(rawQueryTexts)
          .containsExactly("INSERT INTO person VALUES(1); INSERT INTO person VALUES(2)");
    }
  }
}
