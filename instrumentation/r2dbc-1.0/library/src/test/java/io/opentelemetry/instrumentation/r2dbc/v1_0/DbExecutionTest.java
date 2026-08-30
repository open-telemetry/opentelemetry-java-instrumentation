/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbExecutionTest {

  @Mock Connection connection;
  @Mock ConnectionMetadata metadata;

  @SuppressWarnings("deprecation") // testing deprecated semconv
  @Test
  void dbExecution() {
    when(connection.getMetadata()).thenReturn(metadata);
    when(metadata.getDatabaseProductName()).thenReturn("testdb");
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT * from person where last_name = 'tom'"))
            .connectionInfo(MockConnectionInfo.builder().originalConnection(connection).build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:mariadb://root:root@localhost:3306/db");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);
    assertThat(dbExecution.getSystemName()).isEqualTo("mariadb");
    assertThat(dbExecution.getSystem()).isEqualTo("testdb");
    assertThat(dbExecution.getUser()).isEqualTo("root");
    assertThat(dbExecution.getNamespace()).isEqualTo("db");
    assertThat(dbExecution.getServerAddress()).isEqualTo("localhost");
    assertThat(dbExecution.getServerPort()).isEqualTo(3306);
    assertThat(dbExecution.getConnectionString()).isEqualTo("mariadb://localhost:3306");
    assertThat(dbExecution.getRawQueryTexts())
        .containsExactly("SELECT * from person where last_name = 'tom'");
    assertThat(dbExecution.getBatchSize()).isNull();
  }

  @Test
  void dbExecutionWithBatch() {
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

    assertThat(dbExecution.getRawQueryTexts())
        .containsExactly("INSERT INTO person VALUES(1)", "INSERT INTO person VALUES(2)");
    assertThat(dbExecution.getBatchSize()).isEqualTo(2);
  }

  @Test
  void dbExecutionWithBatchSizeOne() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(1)"))
            .batchSize(1)
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db");

    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);

    assertThat(dbExecution.getRawQueryTexts()).containsExactly("INSERT INTO person VALUES(1)");
    assertThat(dbExecution.getBatchSize()).isNull();
  }

  @SuppressWarnings("deprecation") // testing deprecated semconv
  @Test
  void dbExecutionWithPool() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT 1"))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:pool:postgresql://user:pass@dbhost:5432/mydb");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);
    assertThat(dbExecution.getSystemName()).isEqualTo("postgresql");
    assertThat(dbExecution.getSystem()).isEqualTo("other_sql");
    assertThat(dbExecution.getUser()).isEqualTo("user");
    assertThat(dbExecution.getNamespace()).isEqualTo("mydb");
    assertThat(dbExecution.getServerAddress()).isEqualTo("dbhost");
    assertThat(dbExecution.getServerPort()).isEqualTo(5432);
    assertThat(dbExecution.getConnectionString()).isEqualTo("pool:postgresql://dbhost:5432");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MixedCaseDb", "case_sensitive_DB"})
  void dbExecutionPreservesNamespaceCase(String database) {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT 1"))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.DATABASE, database)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);

    assertThat(dbExecution.getNamespace()).isEqualTo(database);
  }

  @ParameterizedTest
  @CsvSource({
    "r2dbc:postgresql://localhost/db, postgresql",
    "r2dbc:mysql://localhost/db, mysql",
    "r2dbc:mariadb://localhost/db, mariadb",
    "r2dbc:mssql://localhost/db, microsoft.sql_server",
    "r2dbc:oracle://localhost/db, oracle.db",
    "r2dbc:db2://localhost/db, ibm.db2",
    "r2dbc:clickhouse://localhost/db, clickhouse",
    "r2dbc:h2:mem:///testdb, h2database",
    "r2dbc:unknown://localhost/db, other_sql",
  })
  void dbSystemName(String url, String expectedSystemName) {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT 1"))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions = ConnectionFactoryOptions.parse(url);
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);
    assertThat(dbExecution.getSystemName()).isEqualTo(expectedSystemName);
  }

  @Test
  void dbExecutionKeepsMultiHostAddressVerbatim() {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:mariadb:sequential://host1:3306,host2:3307/db");

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getServerAddress()).isEqualTo("host1:3306,host2:3307");
    assertThat(dbExecution.getServerPort()).isNull();
    assertThat(dbExecution.getConfiguredServerAddress()).isEqualTo("host1:3306,host2:3307");
    assertThat(dbExecution.getConfiguredServerPort()).isNull();
  }

  @Test
  void dbExecutionGivesEveryHostOfAProgrammaticListThePort() {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "mariadb")
            .option(ConnectionFactoryOptions.HOST, "host1,host2")
            .option(ConnectionFactoryOptions.PORT, 3306)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getServerAddress()).isEqualTo("host1,host2");
    assertThat(dbExecution.getServerPort()).isEqualTo(3306);
    assertThat(dbExecution.getConfiguredServerAddress()).isEqualTo("host1:3306,host2:3306");
    assertThat(dbExecution.getConfiguredServerPort()).isNull();
  }

  @Test
  void dbExecutionKeepsTheHostsThatAlreadyCarryAPort() {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "mariadb")
            .option(
                ConnectionFactoryOptions.HOST, "host1:3307,[2001:db8::1]:3308,host3,[2001:db8::2]")
            .option(ConnectionFactoryOptions.PORT, 3306)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress())
        .isEqualTo("host1:3307,[2001:db8::1]:3308,host3:3306,[2001:db8::2]:3306");
  }

  @Test
  void dbExecutionBracketsUnbracketedIpv6HostsBeforeAddingThePort() {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, "2001:db8::1,2001:db8::2")
            .option(ConnectionFactoryOptions.PORT, 5432)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress())
        .isEqualTo("[2001:db8::1]:5432,[2001:db8::2]:5432");
  }

  @Test
  void dbExecutionBracketsUnbracketedIpv6HostsThatHaveNoPort() {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, "2001:db8::1,2001:db8::2")
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress()).isEqualTo("[2001:db8::1],[2001:db8::2]");
    assertThat(dbExecution.getConfiguredServerPort()).isNull();
  }

  @Test
  void dbExecutionSanitizesUserInfoFromMultiHostAddress() {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, "user:secret@host1,host2")
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress()).isEqualTo("host1,host2");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "host1:invalid,host2",
        "host1,,host2",
        "host1,host=value",
        "[2001:db8::1,host2",
        "host1/path,host2",
        "host1?email=user@example.com,host2",
        "host1#fragment,host2",
        "[]:5432,host2",
        ":5432,host2",
        "host1,not:an:ipv6",
        "host1,[not:an:ipv6]",
        "host1,12345::1",
        "host1,1::999.999.999.999",
        "host1:65536,host2",
        "host1,user:secret@host2,host3"
      })
  void dbExecutionRejectsMalformedMultiHostAddress(String host) {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, host)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress()).isNull();
    assertThat(dbExecution.getConfiguredServerPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 65536, 70000})
  void dbExecutionRejectsOutOfRangeSharedPort(int port) {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, "host1,host2")
            .option(ConnectionFactoryOptions.PORT, port)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress()).isNull();
    assertThat(dbExecution.getConfiguredServerPort()).isNull();
  }

  @Test
  void dbExecutionPreservesUnixDomainSocketWithoutPort() {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, "/var/run/postgresql/.s.PGSQL.5432")
            .option(ConnectionFactoryOptions.PORT, 5432)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress())
        .isEqualTo("/var/run/postgresql/.s.PGSQL.5432");
    assertThat(dbExecution.getConfiguredServerPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/var/run/postgresql,host2",
        "/var/run/postgresql=secret",
        "/var/run/user:secret@postgresql",
        "/var/run/postgresql?password=secret",
        "/var/run/postgresql#fragment"
      })
  void dbExecutionRejectsMalformedOrSensitiveUnixDomainSocket(String host) {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, host)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getConfiguredServerAddress()).isNull();
    assertThat(dbExecution.getConfiguredServerPort()).isNull();
  }

  @ParameterizedTest
  @CsvSource({"host1,host1,5432", "[2001:db8::1],2001:db8::1,5432"})
  void dbExecutionTreatsSingleHostAsSingular(
      String host, String configuredAddress, Integer configuredPort) {
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, host)
            .option(ConnectionFactoryOptions.PORT, 5432)
            .build();

    DbExecution dbExecution = new DbExecution(queryExecutionInfo(), factoryOptions);

    assertThat(dbExecution.getServerAddress()).isEqualTo(host);
    assertThat(dbExecution.getConfiguredServerAddress()).isEqualTo(configuredAddress);
    assertThat(dbExecution.getConfiguredServerPort()).isEqualTo(configuredPort);
  }

  private static QueryExecutionInfo queryExecutionInfo() {
    return MockQueryExecutionInfo.builder()
        .queryInfo(new QueryInfo("SELECT 1"))
        .connectionInfo(MockConnectionInfo.builder().build())
        .build();
  }
}
