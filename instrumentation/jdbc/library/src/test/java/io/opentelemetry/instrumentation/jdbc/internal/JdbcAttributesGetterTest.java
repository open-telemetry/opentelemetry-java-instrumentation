/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // testing deprecated getDbName method
class JdbcAttributesGetterTest {

  private final JdbcAttributesGetter getter = JdbcAttributesGetter.INSTANCE;

  @Test
  void testSqlServerNamedInstanceWithDatabase() {
    // SQL Server with named instance and database should combine: instance|database
    DbInfo dbInfo =
        DbInfo.builder().system("mssql").name("ssinstance").db("ssdb").host("ss.host").build();
    DbRequest request = DbRequest.create(dbInfo, "SELECT 1", false);

    // New semconv db.namespace: combined format per SQL Server semconv
    assertThat(getter.getDbNamespace(request)).isEqualTo("ssinstance|ssdb");
    // Old semconv db.name: returns instance name (takes precedence over db)
    assertThat(getter.getDbName(request)).isEqualTo("ssinstance");
  }

  @Test
  void testSqlServerDefaultInstanceWithDatabase() {
    // SQL Server default instance (no name) with database - no combining needed
    DbInfo dbInfo = DbInfo.builder().system("mssql").db("ssdb").host("ss.host").build();
    DbRequest request = DbRequest.create(dbInfo, "SELECT 1", false);

    assertThat(getter.getDbNamespace(request)).isEqualTo("ssdb");
    assertThat(getter.getDbName(request)).isEqualTo("ssdb");
  }

  @Test
  void testSqlServerNamedInstanceWithoutDatabase() {
    // SQL Server named instance without database - no combining needed
    DbInfo dbInfo = DbInfo.builder().system("mssql").name("ssinstance").host("ss.host").build();
    DbRequest request = DbRequest.create(dbInfo, "SELECT 1", false);

    assertThat(getter.getDbNamespace(request)).isEqualTo("ssinstance");
    assertThat(getter.getDbName(request)).isEqualTo("ssinstance");
  }

  @Test
  void testNonSqlServerWithNameAndDb() {
    // Non-SQL Server databases should NOT use combined format even with both name and db
    DbInfo dbInfo =
        DbInfo.builder()
            .system("oracle")
            .name("orclsn")
            .db("orcldb")
            .host("oracle.host")
            .port(1521)
            .build();
    DbRequest request = DbRequest.create(dbInfo, "SELECT 1", false);

    // Both return just name (no combining)
    assertThat(getter.getDbNamespace(request)).isEqualTo("orclsn");
    assertThat(getter.getDbName(request)).isEqualTo("orclsn");
  }
}
