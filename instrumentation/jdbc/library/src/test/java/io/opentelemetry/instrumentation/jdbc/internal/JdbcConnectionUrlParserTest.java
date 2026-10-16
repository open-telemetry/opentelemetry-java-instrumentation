/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser.parse;
import static io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo.DEFAULT;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractAuthority;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parseServerTargetGroup;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.sanitizeHostList;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.CLICKHOUSE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.DERBY;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.MARIADB;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.MYSQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.POSTGRESQL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcConnectionUrlParserTest {

  private static Properties stdProps() {
    Properties prop = new Properties();
    // https://download.oracle.com/otn-pub/jcp/jdbc-4_1-mrel-spec/jdbc4.1-fr-spec.pdf
    prop.setProperty("databaseName", "stdDatabaseName");
    prop.setProperty("dataSourceName", "stdDatasourceName");
    prop.setProperty("description", "Some description");
    prop.setProperty("networkProtocol", "stdProto");
    prop.setProperty("password", "PASSWORD!");
    prop.setProperty("portNumber", "9999");
    prop.setProperty("roleName", "stdRoleName");
    prop.setProperty("serverName", "stdServerName");
    prop.setProperty("user", "stdUserName");
    return prop;
  }

  private static Properties postgresProps(String user, String currentSchema) {
    Properties prop = new Properties();
    prop.setProperty("user", user);
    prop.setProperty("currentSchema", currentSchema);
    return prop;
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "jdbc:", "jdbc::", "bogus:string"})
  void testInvalidUrlReturnsDefault(String url) {
    assertThat(JdbcConnectionUrlParser.parse(url, null)).isEqualTo(DEFAULT);
  }

  @Test
  void testNullUrlReturnsDefault() {
    assertThat(JdbcConnectionUrlParser.parse(null, null)).isEqualTo(DEFAULT);
  }

  @Test
  void testParserExceptionReturnsBestEffortInfo() {
    // Intentionally malformed Oracle URL: missing subtype/connect info, which triggers the
    // Oracle parser's substring-based failure path after it has already applied defaults/props.
    testVerifySystemSubtypeParsingOfUrl(
        arg("jdbc:oracle:")
            .setProperties(stdProps())
            .setSystem("oracle.db")
            .setHost("stdServerName")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build());
  }

  @Test
  void singletonUrlIsNotMarkedAsMultiTarget() {
    DbInfo dbInfo = parse("jdbc:postgresql://pg.host:5432/db", null);

    assertThat(dbInfo.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("pg.host", null));
  }

  @Test
  void omittedDefaultPortIsNotReportedInConfiguredTarget() {
    DbInfo dbInfo = parse("jdbc:postgresql://pg.host/db", null);

    assertThat(dbInfo.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("pg.host", null));
  }

  @Test
  void commasOutsideAuthorityDoNotMarkSingletonAsMultiTarget() {
    DbInfo dbInfo =
        parse(
            "jdbc:postgresql://pg.host:5432/db"
                + "?user=admin@corp.com&options=search_path=test,public",
            null);

    assertThat(dbInfo.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("pg.host", null));
  }

  @Test
  void commaAfterAtInFinalQueryParameterDoesNotMarkSingletonAsMultiTarget() {
    DbInfo dbInfo = parse("jdbc:postgresql://pg.host:5432/db?password=prefix@domain,suffix", null);

    assertThat(dbInfo.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("pg.host", null));
  }

  @Test
  void commaAfterAtInSqlServerPropertyDoesNotMarkSingletonAsMultiTarget() {
    DbInfo dbInfo = parse("jdbc:sqlserver://ss.host;password=prefix@domain,suffix", null);

    assertThat(dbInfo.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("ss.host", null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:postgresql://h1:5432,unexpected=value/db",
        "jdbc:mariadb:failover://h1:3306,unexpected=value/db",
        "jdbc:unknown://valid.host:1234,evil host:1234/db",
        "jdbc:unknown://address=(host=valid.host)(port=1234),"
            + "address=(host=evil host)(port=1234)/db",
        "jdbc:h2:tcp://h1:8082,h2:8083/db",
        "jdbc:sqlserver://;failoverPartner=h2",
        "jdbc:sqlserver://h1;failoverPartner=unexpected=value",
        "jdbc:oracle:thin:@//h1,unexpected=value/service",
        "jdbc:oracle:thin:@ldap://ldap1:389,ldap2:389/cn=oraclecontext",
        "jdbc:oracle:thin:@(description=(address=(host=h1)(port=1521))"
            + "(address=(host=h2)(port=1522)"
      })
  void incompleteMultiTargetIsMarkedWithoutAConfiguredTarget(String url) {
    DbInfo dbInfo = parse(url, null);

    assertThat(dbInfo.getConfiguredServerTarget()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:mariadb:failover://user:p,a/ss@h1:3306,h2:3306/db",
        "jdbc:mariadb:failover://user:p,a?ss@h1:3306,h2:3306/db",
        "jdbc:mariadb:failover://user:p,a#ss@h1:3306,h2:3306/db",
        "jdbc:mariadb:failover://user:123,a?x=y@h1:3306,h2:3306/db",
        "jdbc:mariadb:failover://user:123,a?x=y@address=(host=h1),address=(host=h2)/db",
        "jdbc:mariadb:failover://user:123,a?x=y@address=(host=h1)/db",
        "jdbc:mariadb:failover://user:123,a#ignored?x=y@/db"
      })
  void ambiguousMariaDbCredentialsDoNotBecomeAConfiguredTarget(String url) {
    DbInfo dbInfo = parse(url, null);
    assertThat(dbInfo.getConfiguredServerTarget()).isNull();
    assertThat(dbInfo.getDbNamespace()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:postgresql://user:123,a/ss@pg.host:5432",
        "jdbc:postgresql://user:123,a#ignored?x=y@pg.host:5432,pg2:5432/db"
      })
  void ambiguousPostgresCredentialsDoNotBecomeAConfiguredTarget(String url) {
    DbInfo dbInfo = parse(url, null);
    assertThat(dbInfo.getConfiguredServerTarget()).isNull();
    assertThat(dbInfo.getDbNamespace()).isNull();
  }

  @Test
  void atInPostgresQueryDoesNotDisableConfiguredTargetParsing() {
    DbInfo dbInfo =
        parse(
            "jdbc:postgresql://pg.host1:5432,pg.host2:5433/pgdb"
                + "?user=admin@corp.com&currentSchema=test,public",
            null);
    assertThat(dbInfo.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("pg.host1:5432,pg.host2:5433", null));
    assertThat(dbInfo.getDbNamespace()).isEqualTo("pgdb|test,public");
  }

  @Test
  void atInPostgresQueryWithoutDatabaseDoesNotDisableConfiguredTargetParsing() {
    DbInfo dbInfo =
        parse("jdbc:postgresql://h1:5432,h2:5432?options=email=user@example.com,mode=strict", null);

    assertThat(dbInfo.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("h1,h2", null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "&serverSslCert=/etc/ssl/ca.pem",
        "&sessionVariables=sql_mode=ANSI,time_zone=UTC",
        "&sessionVariables=email='user@example.com',sql_mode=ANSI"
      })
  void atInMariaDbQueryDoesNotDisableConfiguredTargetParsing(String trailingParameters) {
    String url =
        "jdbc:mariadb:failover://h1:3306,h2:3306/db?user="
            + "admin"
            + "@"
            + "corp.com"
            + trailingParameters;

    DbInfo dbInfo = parse(url, null);

    assertThat(dbInfo.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("h1,h2", null));
    assertThat(dbInfo.getDbNamespace()).isEqualTo("db");
  }

  @Test
  void hostSpecificPropertiesAreExcludedFromConfiguredTargets() {
    assertThat(
            sanitizeHostList(
                "address=(host=h1)(port=3306)(trustCertificateKeyStorePassword=secret),"
                    + "address=(host=h2)(port=3307)(customProperty=value)"))
        .isEqualTo("address=(host=h1)(port=3306),address=(host=h2)(port=3307)");
  }

  @Test
  void malformedHostEntriesAreExcludedFromConfiguredTargets() {
    assertThat(sanitizeHostList("h1:3306,unexpected=value")).isNull();
    assertThat(sanitizeHostList("address=(host=h1),address=(host=unexpected=value)")).isNull();
    assertThat(sanitizeHostList("h1:5432,:5433")).isNull();
    assertThat(sanitizeHostList("not:an:address,h2")).isNull();
    assertThat(sanitizeHostList("address=(host=h1)(host=h2),address=(host=h3)")).isNull();
    assertThat(sanitizeHostList("address=(host=h1)(port=secret),address=(host=h2)(port=3306)"))
        .isNull();
    assertThat(
            sanitizeHostList(
                "address=(host=h1)(port=3306)(port=3307),address=(host=h2)(port=3306)"))
        .isNull();
  }

  @Test
  void ipv6ZoneIdentifiersArePreservedInConfiguredTargets() {
    assertThat(sanitizeHostList("fe80::1%eth0,fe80::2%eth1"))
        .isEqualTo("fe80::1%eth0,fe80::2%eth1");
  }

  @Test
  void atInMariaDbQueryWithoutDatabaseDoesNotDisableConfiguredTargetParsing() {
    DbInfo dbInfo =
        parse(
            "jdbc:mariadb:failover://h1,h2"
                + "?sessionVariables=email='user@example.com',sql_mode=ANSI",
            null);

    assertThat(dbInfo.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("h1,h2", null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:mariadb:failover://h1" + "?sessionVariables=email='user@example.com',sql_mode=ANSI",
        "jdbc:mariadb:failover://address=(host=h1)"
            + "?sessionVariables=email='user@example.com',sql_mode=ANSI"
      })
  void atInMariaDbSingletonQueryPreservesOrdinaryParsing(String url) {
    DbInfo dbInfo = parse(url, null);

    assertThat(dbInfo.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("h1", null));
  }

  @Test
  void atInMariaDbAddressBlockQueryWithoutDatabaseDoesNotDisableConfiguredTargetParsing() {
    DbInfo dbInfo =
        parse(
            "jdbc:mariadb:failover://address=(host=h1),address=(host=h2)"
                + "?sessionVariables=email='user@example.com',sql_mode=ANSI",
            null);

    assertThat(dbInfo.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("h1,h2", null));
  }

  private static Stream<Arguments> mySqlArguments() {
    return argsWithDefaultPort(
        3306,
        // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
        // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
        arg("jdbc:mysql:///")
            .setSystem(MYSQL)
            .setHost("localhost")
            .setPort(3306)
            .setNoConfiguredTarget()
            .build(),
        arg("jdbc:mysql:///")
            .setProperties(stdProps())
            .setSystem(MYSQL)
            .setHost("stdServerName")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:mysql://my.host").setSystem(MYSQL).setHost("my.host").setPort(3306).build(),
        arg("jdbc:mysql://my.host?user=myuser&password=PW")
            .setSystem(MYSQL)
            .setHost("my.host")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql://my.host:22/mydb?user=myuser&password=PW")
            .setSystem(MYSQL)
            .setHost("my.host")
            .setPort(22)
            .setName("mydb")
            .build(),
        arg("jdbc:mysql://127.0.0.1:22/mydb?user=myuser&password=PW")
            .setProperties(stdProps())
            .setSystem(MYSQL)
            .setHost("127.0.0.1")
            .setPort(22)
            .setName("mydb")
            .build(),
        arg("jdbc:mysql://myuser:password@my.host:22/mydb")
            .setSystem(MYSQL)
            .setHost("my.host")
            .setPort(22)
            .setName("mydb")
            .build(),
        arg("jdbc:mysql:aurora://mdb.host/mdbdb")
            .setSystem(MYSQL)
            .setHost("mdb.host")
            .setPort(3306)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mysql:failover://localhost/mdbdb?autoReconnect=true")
            .setSystem(MYSQL)
            .setHost("localhost")
            .setPort(3306)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mysql:failover://localhost:1234?autoReconnect=true")
            .setSystem(MYSQL)
            .setHost("localhost")
            .setPort(1234)
            .build(),
        arg("jdbc:mysql:failover://my.host?user=domain:user")
            .setSystem(MYSQL)
            .setHost("my.host")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql:loadbalance://127.0.0.1,127.0.0.1:3306/mdbdb?user=mdbuser&password=PW")
            .setSystem(MYSQL)
            .setHost("127.0.0.1")
            .setPort(3306)
            .setName("mdbdb")
            .setServerAddressGroup("127.0.0.1,127.0.0.1")
            .build(),
        arg("jdbc:mysql:replication://address=(HOST=127.0.0.1)(port=33)(user=mdbuser)(password=PW),address=(host=mdb.host)(port=3306)(user=otheruser)(password=PW)/mdbdb?user=wrong&password=PW")
            .setSystem(MYSQL)
            .setHost("127.0.0.1")
            .setPort(33)
            .setName("mdbdb")
            .setServerAddressGroup("127.0.0.1:33,mdb.host:3306")
            .build(),
        arg("jdbc:mysql:replication://address=(HOST=mdb.host),address=(host=anotherhost)(port=3306)(user=wrong)(password=PW)/mdbdb?user=mdbuser&password=PW")
            .setSystem(MYSQL)
            .setHost("mdb.host")
            .setPort(3306)
            .setName("mdbdb")
            .setServerAddressGroup("mdb.host,anotherhost")
            .build(),
        arg("jdbc:mysql:replication://address=(host=::1)(port=33)/mydb")
            .setSystem(MYSQL)
            .setHost("::1")
            .setPort(33)
            .setName("mydb")
            .build(),
        arg("jdbc:mysql:loadbalance://localhost")
            .setSystem(MYSQL)
            .setHost("localhost")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql:loadbalance://host:3306") // with port but no slash
            .setSystem(MYSQL)
            .setHost("host")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql:failover://[::1]:3306") // IPv6 without slash
            .setSystem(MYSQL)
            .setHost("::1")
            .setPort(3306)
            .build(),
        // literal IPv6 address: server.address holds the address without the URL brackets
        arg("jdbc:mysql://[::1]:3306/mydb")
            .setSystem(MYSQL)
            .setHost("::1")
            .setPort(3306)
            .setName("mydb")
            .build(),
        arg("jdbc:mysql:host:3306").setSystem(MYSQL).setHost("host").setPort(3306).build(),
        arg("jdbc:mysql:host").setSystem(MYSQL).setHost("host").setPort(3306).build(),
        arg("jdbc:mysql:my.host:1234?user=myuser&password=PW")
            .setSystem(MYSQL)
            .setHost("my.host")
            .setPort(1234)
            .build(),
        arg("jdbc:mysql:my.host?user=myuser&password=PW")
            .setSystem(MYSQL)
            .setHost("my.host")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql:my.host?socket=/tmp/mysql.sock")
            .setSystem(MYSQL)
            .setHost("my.host")
            .setPort(3306)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("mySqlArguments")
  void testMySqlParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> clickHouseArguments() {
    return args(
        // https://clickhouse.com/docs/integrations/language-clients/java/jdbc#configuration
        arg("jdbc:clickhouse:http://localhost:8123/mydb")
            .setSystem(CLICKHOUSE)
            .setHost("localhost")
            .setPort(8123)
            .setName("mydb")
            .build(),
        arg("jdbc:clickhouse:https://localhost:8443?ssl=true")
            .setSystem(CLICKHOUSE)
            .setHost("localhost")
            .setPort(8443)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("clickHouseArguments")
  void testClickHouseParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> postgresArguments() {
    return argsWithDefaultPort(
        5432,
        // https://jdbc.postgresql.org/documentation/94/connect.html
        arg("jdbc:postgresql:///")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setNoConfiguredTarget()
            .build(),
        arg("jdbc:postgresql:///")
            .setProperties(stdProps())
            .setSystem(POSTGRESQL)
            .setHost("stdServerName")
            .setPort(9999)
            .setNamespace("stdDatabaseName|stdUserName")
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:postgresql://pg.host")
            .setSystem(POSTGRESQL)
            .setHost("pg.host")
            .setPort(5432)
            .build(),
        arg("jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW")
            .setSystem(POSTGRESQL)
            .setHost("pg.host")
            .setPort(11)
            .setNamespace("pgdb|pguser")
            .setName("pgdb")
            .build(),
        arg("jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW")
            .setProperties(stdProps())
            .setSystem(POSTGRESQL)
            .setHost("pg.host")
            .setPort(11)
            .setNamespace("pgdb|pguser")
            .setName("pgdb")
            .build(),
        // currentSchema param takes precedence over user for namespace
        arg("jdbc:postgresql://pg.host:11/pgdb?user=pguser&currentSchema=myschema")
            .setSystem("postgresql")
            .setHost("pg.host")
            .setPort(11)
            .setNamespace("pgdb|myschema")
            .setName("pgdb")
            .build(),
        // currentSchema without user
        arg("jdbc:postgresql://pg.host/pgdb?currentSchema=myschema")
            .setSystem("postgresql")
            .setHost("pg.host")
            .setPort(5432)
            .setNamespace("pgdb|myschema")
            .setName("pgdb")
            .build(),
        // currentSchema from connection properties is used when the URL does not specify it
        arg("jdbc:postgresql://pg.host/pgdb")
            .setProperties(postgresProps("pguser", "propertyschema"))
            .setSystem("postgresql")
            .setHost("pg.host")
            .setPort(5432)
            .setNamespace("pgdb|propertyschema")
            .setName("pgdb")
            .build(),
        // currentSchema URL param takes precedence over currentSchema property
        arg("jdbc:postgresql://pg.host/pgdb?currentSchema=urlschema")
            .setProperties(postgresProps("pguser", "propertyschema"))
            .setSystem("postgresql")
            .setHost("pg.host")
            .setPort(5432)
            .setNamespace("pgdb|urlschema")
            .setName("pgdb")
            .build(),
        // database only, no schema or user — namespace falls back to database name
        arg("jdbc:postgresql://pg.host/pgdb")
            .setSystem("postgresql")
            .setHost("pg.host")
            .setPort(5432)
            .setName("pgdb")
            .build(),
        // literal IPv6 address: server.address holds the address without the URL brackets
        arg("jdbc:postgresql://[2001:db8::1]:5432/pgdb")
            .setSystem(POSTGRESQL)
            .setHost("2001:db8::1")
            .setPort(5432)
            .setName("pgdb")
            .build(),
        arg("jdbc:postgresql://[2001:db8::1]/pgdb")
            .setSystem(POSTGRESQL)
            .setHost("2001:db8::1")
            .setPort(5432)
            .setName("pgdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("postgresArguments")
  void testPostgresParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> mariaDbArguments() {
    return argsWithDefaultPort(
        3306,
        // https://mariadb.com/kb/en/library/about-mariadb-connector-j/#connection-strings
        arg("jdbc:mariadb:127.0.0.1:33/mdbdb")
            .setSystem(MARIADB)
            .setHost("127.0.0.1")
            .setPort(33)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mariadb:localhost/mdbdb")
            .setSystem(MARIADB)
            .setHost("localhost")
            .setPort(3306)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mariadb:localhost/mdbdb?user=mdbuser&password=PW")
            .setProperties(stdProps())
            .setSystem(MARIADB)
            .setHost("localhost")
            .setPort(9999)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mariadb:localhost:33/mdbdb")
            .setProperties(stdProps())
            .setSystem(MARIADB)
            .setHost("localhost")
            .setPort(33)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mariadb://mdb.host:33/mdbdb?user=mdbuser&password=PW")
            .setSystem(MARIADB)
            .setHost("mdb.host")
            .setPort(33)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mariadb:aurora://mdb.host/mdbdb")
            .setSystem(MARIADB)
            .setHost("mdb.host")
            .setPort(3306)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mariadb:failover://mdb.host1:33,mdb.host/mdbdb?characterEncoding=utf8")
            .setSystem(MARIADB)
            .setHost("mdb.host1")
            .setPort(33)
            .setName("mdbdb")
            .setServerAddressGroup("mdb.host1:33,mdb.host:3306")
            .build(),
        arg("jdbc:mariadb:sequential://mdb.host1,mdb.host2:33/mdbdb")
            .setSystem(MARIADB)
            .setHost("mdb.host1")
            .setPort(3306)
            .setName("mdbdb")
            .setServerAddressGroup("mdb.host1:3306,mdb.host2:33")
            .build(),
        arg("jdbc:mariadb:loadbalance://127.0.0.1:33,mdb.host/mdbdb")
            .setSystem(MARIADB)
            .setHost("127.0.0.1")
            .setPort(33)
            .setName("mdbdb")
            .setServerAddressGroup("127.0.0.1:33,mdb.host:3306")
            .build(),
        arg("jdbc:mariadb:loadbalance://127.0.0.1:33/mdbdb")
            .setSystem(MARIADB)
            .setHost("127.0.0.1")
            .setPort(33)
            .setName("mdbdb")
            .build(),
        arg("jdbc:mariadb:loadbalance://[2001:0660:7401:0200:0000:0000:0edf:bdd7]:33,mdb.host/mdbdb")
            .setSystem(MARIADB)
            .setHost("2001:0660:7401:0200:0000:0000:0edf:bdd7")
            .setPort(33)
            .setName("mdbdb")
            .setServerAddressGroup("[2001:0660:7401:0200:0000:0000:0edf:bdd7]:33,mdb.host:3306")
            .build(),
        arg("jdbc:mariadb:replication://localhost:33,anotherhost:3306/mdbdb")
            .setSystem(MARIADB)
            .setHost("localhost")
            .setPort(33)
            .setName("mdbdb")
            .setServerAddressGroup("localhost:33,anotherhost:3306")
            .build(),
        arg("jdbc:mariadb:loadbalance://localhost")
            .setSystem(MARIADB)
            .setHost("localhost")
            .setPort(3306)
            .build(),
        arg("jdbc:mariadb:loadbalance://host:3306") // with port but no slash
            .setSystem(MARIADB)
            .setHost("host")
            .setPort(3306)
            .build(),
        arg("jdbc:mariadb:failover://[::1]:3306") // IPv6 without slash
            .setSystem(MARIADB)
            .setHost("::1")
            .setPort(3306)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("mariaDbArguments")
  void testMariaDbParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> sqlServerArguments() {
    return argsWithDefaultPort(
        1433,
        // https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
        arg("jdbc:microsoft:sqlserver://;")
            .setSystem("microsoft.sql_server")
            .setHost("localhost")
            .setPort(1433)
            .build(),
        arg("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
            .setSystem("microsoft.sql_server")
            .setHost("3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
            .setPort(1433)
            .build(),
        arg("jdbc:sqlserver://;serverName=2001:0db8:85a3:0000:0000:8a2e:0370:7334")
            .setSystem("microsoft.sql_server")
            .setHost("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
            .setPort(1433)
            .build(),
        arg("jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43")
            .setSystem("microsoft.sql_server")
            .setHost("3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
            .setPort(43)
            .build(),
        arg("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance")
            .setSystem("microsoft.sql_server")
            .setHost("3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
            .setPort(1433)
            .setName("ssinstance")
            .build(),
        arg("jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance]:43")
            .setSystem("microsoft.sql_server")
            .setHost("3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
            .setPort(43)
            .setName("ssinstance")
            .build(),
        arg("jdbc:sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]\\ssinstance;databaseName=ssdb")
            .setSystem("microsoft.sql_server")
            .setHost("3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
            .setPort(1433)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        arg("jdbc:sqlserver://[::1]:1433;databaseName=ssdb")
            .setSystem("microsoft.sql_server")
            .setHost("::1")
            .setPort(1433)
            .setName("ssdb")
            .build(),
        arg("jdbc:sqlserver://ss.host1;instanceName=instance1;databaseName=ssdb")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host1")
            .setPort(1433)
            .setName("ssdb")
            .build(),
        arg("jdbc:microsoft:sqlserver://;")
            .setProperties(stdProps())
            .setSystem("microsoft.sql_server")
            .setHost("stdServerName")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:sqlserver://ss.host\\ssinstance:44;databaseName=ssdb;user=ssuser;password=pw")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(44)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        arg("jdbc:sqlserver://;serverName=ss.host\\ssinstance:44;DatabaseName=;")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(44)
            .setName("ssinstance")
            .build(),
        arg("jdbc:sqlserver://ss.host;serverName=althost;DatabaseName=ssdb;")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setName("ssdb")
            .build(),
        // database= alias (shorthand for databaseName)
        arg("jdbc:sqlserver://ss.host;database=ssdb;")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setName("ssdb")
            .build(),
        arg("jdbc:sqlserver://ss.host\\ssinstance:44;database=ssdb;user=ssuser")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(44)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        arg("jdbc:microsoft:sqlserver://ss.host:44;DatabaseName=ssdb;user=ssuser;password=pw;user=ssuser2;")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(44)
            .setName("ssdb")
            .build(),
        arg("jdbc:sqlserver://ss.host:44/urldb;user=ssuser")
            .setProperties(stdProps())
            .setSystem("microsoft.sql_server")
            .setHost("stdServerName")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:sqlserver://ss.host\\ssinstance:44;databaseName=urldb;user=ssuser")
            .setProperties(stdProps())
            .setSystem("microsoft.sql_server")
            .setHost("stdServerName")
            .setPort(9999)
            .setNamespace("ssinstance|stdDatabaseName")
            .setName("ssinstance")
            .build(),

        // http://jtds.sourceforge.net/faq.html#urlFormat
        arg("jdbc:jtds:sqlserver://ss.host/ssdb")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setName("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setName("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb;user=ssuser")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setName("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host/ssdb;instance=ssinstance")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1444/ssdb;instance=ssinstance")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1444)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb;instance=ssinstance;user=ssuser")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        // instance without database — namespace is just the instance name
        arg("jdbc:jtds:sqlserver://ss.host;instance=ssinstance")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setNamespace("ssinstance")
            .setName("ssinstance")
            .build(),
        // database= alias (shorthand for databaseName) in jTDS URLs
        arg("jdbc:jtds:sqlserver://ss.host/ssdb;instance=ssinstance;database=otherdb")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        // database= param provides database name when there's no URL path
        arg("jdbc:jtds:sqlserver://ss.host;instance=ssinstance;database=ssdb")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host")
            .setPort(1433)
            .setNamespace("ssinstance|ssdb")
            .setName("ssinstance")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1444/urldb")
            .setProperties(stdProps())
            .setSystem("microsoft.sql_server")
            .setHost("stdServerName")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1444/urldb;instance=ssinstance")
            .setProperties(stdProps())
            .setSystem("microsoft.sql_server")
            .setHost("stdServerName")
            .setPort(9999)
            .setNamespace("ssinstance|stdDatabaseName")
            .setName("ssinstance")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("sqlServerArguments")
  void testSqlServerParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  @Test
  void sqlServerDataSourceFailoverPartnerOverridesUrlPartner() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "property.host1");
    properties.setProperty("instanceName", "propertyInstance");
    properties.setProperty("portNumber", "1444");
    properties.setProperty("failoverPartner", "property.host2");

    DbInfo info = parse("jdbc:sqlserver://url.host1:1433;failoverPartner=url.host2", properties);

    assertThat(info.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("property.host1:1444,property.host2:1433", null));
  }

  @Test
  void oracleEasyConnectListParsesFirstEndpointAndService() {
    DbInfo info =
        parse(
            "jdbc:oracle:thin:@tcps://[2001:db8::1]:2521,"
                + "[2001:db8::2]:2521/orclsn?retry_count=3",
            null);

    assertThat(info.getDbNamespace()).isEqualTo("orclsn");
    assertThat(info.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("[2001:db8::1]:2521,[2001:db8::2]:2521", null));
  }

  @Test
  void oracleLdapDiscoveryTargetOmitsConnectionParameters() {
    DbInfo info =
        parse(
            "jdbc:oracle:thin:@ldap://orcl.host:389/some,cn=OracleContext,dc=com"
                + "?connect_timeout=5",
            null);

    assertThat(info.getConfiguredServerTarget())
        .isEqualTo(
            DbServerTarget.create("ldap://orcl.host:389/some,cn=oraclecontext,dc=com", null));
  }

  private static Stream<Arguments> oracleArguments() {
    return argsWithDefaultPort(
        1521,
        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/urls.htm
        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/jdbcthin.htm
        arg("jdbc:oracle:thin:orcluser/PW@localhost:55:orclsn")
            .setSystem("oracle.db")
            .setHost("localhost")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcluser/PW@//orcl.host:55/orclsn")
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcluser/PW@127.0.0.1:orclsn")
            .setSystem("oracle.db")
            .setHost("127.0.0.1")
            .setPort(1521) // Default Oracle port assumed as not specified in the URL
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcluser/PW@//orcl.host/orclsn")
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(1521) // Default Oracle port assumed as not specified in the URL
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:@//orcl.host:55/orclsn")
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:@ldap://orcl.host:55/some,cn=OracleContext,dc=com")
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(55)
            .setName("some,cn=oraclecontext,dc=com")
            .setConfiguredServerTarget("ldap://orcl.host:55/some,cn=oraclecontext,dc=com", null)
            .build(),
        arg("jdbc:oracle:thin:@ldaps://orcl.host:636/some,cn=OracleContext,dc=com")
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(636)
            .setName("some,cn=oraclecontext,dc=com")
            .setConfiguredServerTarget("ldaps://orcl.host:636/some,cn=oraclecontext,dc=com", null)
            .build(),
        arg("jdbc:oracle:thin:127.0.0.1:orclsn")
            .setSystem("oracle.db")
            .setHost("127.0.0.1")
            .setPort(1521) // Default Oracle port assumed as not specified in the URL
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcl.host:orclsn")
            .setProperties(stdProps())
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(9999)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=127.0.0.1)(PORT=666))"
                + "(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=orclsn)))")
            .setSystem("oracle.db")
            .setHost("127.0.0.1")
            .setPort(666)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:@ ( description = (connect_timeout=90)(retry_count=20)(retry_delay=3) (transport_connect_timeout=3000) (address_list = (load_balance = on) (failover = on) (address = (protocol = tcp)(host = orcl.host1 )(port = 1521 )) (address = (protocol = tcp)(host = orcl.host2)(port = 1521)) (address = (protocol = tcp)(host = orcl.host3)(port = 1521)) (address = (protocol = tcp)(host = orcl.host4)(port = 1521)) ) (connect_data = (server = dedicated) (service_name = orclsn)))")
            .setSystem("oracle.db")
            .setHost("orcl.host1")
            .setPort(1521)
            .setName("orclsn")
            .setServerAddressGroup("orcl.host1,orcl.host2,orcl.host3,orcl.host4")
            .build(),

        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/instclnt.htm
        arg("jdbc:oracle:drivertype:orcluser/PW@orcl.host:55/orclsn")
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:oci8:@")
            .setSystem("oracle.db")
            .setPort(1521)
            .setNoConfiguredTarget()
            .build(),
        arg("jdbc:oracle:oci8:@")
            .setProperties(stdProps())
            .setSystem("oracle.db")
            .setHost("stdServerName")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:oracle:oci8:@orclsn")
            .setSystem("oracle.db")
            .setPort(1521)
            .setName("orclsn")
            .setNoConfiguredTarget()
            .build(),
        arg("jdbc:oracle:oci:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=orcl.host)(PORT=55))(CONNECT_DATA=(SERVICE_NAME=orclsn)))")
            .setSystem("oracle.db")
            .setHost("orcl.host")
            .setPort(55)
            .setName("orclsn")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("oracleArguments")
  void testOracleParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> db2Arguments() {
    return argsWithDefaultPort(
        50000,
        // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_tjvjcccn.html
        // https://www.ibm.com/support/knowledgecenter/en/SSEPGG_10.5.0/com.ibm.db2.luw.apdv.java.doc/src/tpc/imjcc_r0052342.html
        arg("jdbc:db2://db2.host").setSystem("ibm.db2").setHost("db2.host").setPort(50000).build(),
        arg("jdbc:db2://db2.host")
            .setProperties(stdProps())
            .setSystem("ibm.db2")
            .setHost("db2.host")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;")
            .setSystem("ibm.db2")
            .setHost("db2.host")
            .setPort(77)
            .setName("db2db")
            .build(),
        arg("jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;")
            .setProperties(stdProps())
            .setSystem("ibm.db2")
            .setHost("db2.host")
            .setPort(77)
            .setName("db2db")
            .build(),
        arg("jdbc:as400://ashost:66/asdb:user=asuser;password=PW;")
            .setSystem("ibm.db2")
            .setHost("ashost")
            .setPort(66)
            .setName("asdb")
            .build(),
        // literal IPv6 address: server.address holds the address without the URL brackets
        arg("jdbc:db2://[::1]:77/db2db")
            .setSystem("ibm.db2")
            .setHost("::1")
            .setPort(77)
            .setName("db2db")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("db2Arguments")
  void testDb2Parsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> sapArguments() {
    return args(
        // https://help.sap.com/viewer/0eec0d68141541d1b07893a39944924e/2.0.03/en-US/ff15928cf5594d78b841fbbe649f04b4.html
        arg("jdbc:sap://sap.host").setSystem("sap.hana").setHost("sap.host").build(),
        arg("jdbc:sap://sap.host")
            .setProperties(stdProps())
            .setSystem("sap.hana")
            .setHost("sap.host")
            .setPort(9999)
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:sap://sap.host:88/?databaseName=sapdb&user=sapuser&password=PW")
            .setSystem("sap.hana")
            .setHost("sap.host")
            .setPort(88)
            .setName("sapdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("sapArguments")
  void testSapParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> informixArguments() {
    return argsWithDefaultPort(
        9088,
        // https://www.ibm.com/support/pages/how-configure-informix-jdbc-connection-string-connect-group
        arg("jdbc:informix-sqli://infxhost:99/infxdb:INFORMIXSERVER=infxsn;user=infxuser;password=PW")
            .setSystem("ibm.informix")
            .setHost("infxhost")
            .setPort(99)
            .setName("infxdb")
            .build(),
        arg("jdbc:informix-sqli://localhost:9088/stores_demo:INFORMIXSERVER=informix")
            .setSystem("ibm.informix")
            .setHost("localhost")
            .setPort(9088)
            .setName("stores_demo")
            .build(),
        arg("jdbc:informix-sqli://infxhost:99")
            .setSystem("ibm.informix")
            .setHost("infxhost")
            .setPort(99)
            .build(),
        arg("jdbc:informix-sqli://infxhost/")
            .setSystem("ibm.informix")
            .setHost("infxhost")
            .setPort(9088)
            .build(),
        arg("jdbc:informix-sqli:").setSystem("ibm.informix").setPort(9088).build(),

        // https://www.ibm.com/docs/en/informix-servers/12.10?topic=method-format-database-urls
        arg("jdbc:informix-direct://infxdb:999;user=infxuser;password=PW")
            .setSystem("ibm.informix")
            .setName("infxdb")
            .build(),
        arg("jdbc:informix-direct://infxdb;user=infxuser;password=PW")
            .setSystem("ibm.informix")
            .setName("infxdb")
            .build(),
        arg("jdbc:informix-direct://infxdb").setSystem("ibm.informix").setName("infxdb").build(),
        arg("jdbc:informix-direct:").setSystem("ibm.informix").build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("informixArguments")
  void testInformixParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> h2Arguments() {
    return args(
        // http://www.h2database.com/html/features.html#database_url
        arg("jdbc:h2:mem:").setSystem("h2database").build(),
        arg("jdbc:h2:mem:")
            .setProperties(stdProps())
            .setSystem("h2database")
            .setName("stdDatabaseName")
            .build(),
        arg("jdbc:h2:mem:h2db").setSystem("h2database").setName("h2db").build(),
        arg("jdbc:h2:tcp://h2.host:111/path/h2db;user=h2user;password=PW")
            .setSystem("h2database")
            .setHost("h2.host")
            .setPort(111)
            .setName("path/h2db")
            .build(),
        arg("jdbc:h2:ssl://h2.host:111/path/h2db;user=h2user;password=PW")
            .setSystem("h2database")
            .setHost("h2.host")
            .setPort(111)
            .setName("path/h2db")
            .build(),
        arg("jdbc:h2:/data/h2file").setSystem("h2database").setName("/data/h2file").build(),
        arg("jdbc:h2:file:~/h2file;USER=h2user;PASSWORD=PW")
            .setSystem("h2database")
            .setName("~/h2file")
            .build(),
        arg("jdbc:h2:file:/data/h2file").setSystem("h2database").setName("/data/h2file").build(),
        arg("jdbc:h2:file:C:/data/h2file")
            .setSystem("h2database")
            .setName("c:/data/h2file")
            .build(),
        arg("jdbc:h2:zip:~/db.zip!/h2zip")
            .setSystem("h2database")
            .setName("~/db.zip!/h2zip")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("h2Arguments")
  void testH2Parsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> hsqlDbArguments() {
    return args(
        // http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html
        arg("jdbc:hsqldb:hsdb").setSystem(HSQLDB).setName("hsdb").build(),
        arg("jdbc:hsqldb:hsdb").setProperties(stdProps()).setSystem(HSQLDB).setName("hsdb").build(),
        arg("jdbc:hsqldb:mem:hsdb").setSystem(HSQLDB).setName("hsdb").build(),
        arg("jdbc:hsqldb:mem:hsdb;shutdown=true").setSystem(HSQLDB).setName("hsdb").build(),
        arg("jdbc:hsqldb:mem:hsdb?shutdown=true").setSystem(HSQLDB).setName("hsdb").build(),
        arg("jdbc:hsqldb:file:hsdb").setSystem(HSQLDB).setName("hsdb").build(),
        arg("jdbc:hsqldb:file:hsdb;user=aUserName;password=3xLVz")
            .setSystem(HSQLDB)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:file:hsdb;create=false?user=aUserName&password=3xLVz")
            .setSystem(HSQLDB)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:file:/loc/hsdb").setSystem(HSQLDB).setName("/loc/hsdb").build(),
        arg("jdbc:hsqldb:file:C:/hsdb").setSystem(HSQLDB).setName("c:/hsdb").build(),
        arg("jdbc:hsqldb:res:hsdb").setSystem(HSQLDB).setName("hsdb").build(),
        arg("jdbc:hsqldb:res:/cp/hsdb").setSystem(HSQLDB).setName("/cp/hsdb").build(),
        arg("jdbc:hsqldb:hsql://hs.host:333/hsdb")
            .setSystem(HSQLDB)
            .setHost("hs.host")
            .setPort(333)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:hsqls://hs.host/hsdb")
            .setSystem(HSQLDB)
            .setHost("hs.host")
            .setPort(9001)
            .setConfiguredServerTarget("hs.host", null)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:http://hs.host")
            .setSystem(HSQLDB)
            .setHost("hs.host")
            .setPort(80)
            .setConfiguredServerTarget("hs.host", null)
            .build(),
        arg("jdbc:hsqldb:http://hs.host:333/hsdb")
            .setSystem(HSQLDB)
            .setHost("hs.host")
            .setPort(333)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:https://127.0.0.1/hsdb")
            .setSystem(HSQLDB)
            .setHost("127.0.0.1")
            .setPort(443)
            .setConfiguredServerTarget("127.0.0.1", null)
            .setName("hsdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("hsqlDbArguments")
  void testHsqlDbParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> derbyArguments() {
    return argsWithDefaultPort(
        1527,
        // https://db.apache.org/derby/papers/DerbyClientSpec.html#Connection+URL+Format
        // https://db.apache.org/derby/docs/10.8/devguide/cdevdvlp34964.html
        arg("jdbc:derby:derbydb").setSystem(DERBY).setName("derbydb").build(),
        arg("jdbc:derby:derbydb")
            .setProperties(stdProps())
            .setSystem(DERBY)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:derbydb;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:memory:derbydb").setSystem(DERBY).setName("derbydb").build(),
        arg("jdbc:derby:memory:;databaseName=derbydb").setSystem(DERBY).setName("derbydb").build(),
        arg("jdbc:derby:memory:derbydb;databaseName=altdb")
            .setSystem(DERBY)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:memory:derbydb;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby://derby.host:222/memory:derbydb;create=true")
            .setSystem(DERBY)
            .setHost("derby.host")
            .setPort(222)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby://derby.host/memory:derbydb;create=true;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setHost("derby.host")
            .setPort(1527)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby://127.0.0.1:1527/memory:derbydb;create=true;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setHost("127.0.0.1")
            .setPort(1527)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:directory:derbydb;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:classpath:/some/derbydb;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setName("/some/derbydb")
            .build(),
        arg("jdbc:derby:jar:/derbydb;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setName("/derbydb")
            .build(),
        arg("jdbc:derby:jar:(~/path/to/db.jar)/other/derbydb;user=derbyuser;password=pw")
            .setSystem(DERBY)
            .setName("(~/path/to/db.jar)/other/derbydb")
            .build(),
        arg("jdbc:derby:directory:/usr/ibm/pep/was9/ibm/websphere/appserver/profiles/my_profile/databases/ejbtimers/myhostname/ejbtimerdb")
            .setSystem(DERBY)
            .setName("ejbtimerdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("derbyArguments")
  void testDerbyParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> dataDirectArguments() {
    return args(
        // https://docs.progress.com/bundle/datadirect-connect-jdbc-51/page/URL-Formats-DataDirect-Connect-for-JDBC-Drivers.html
        arg("jdbc:datadirect:sqlserver://server_name:1433;DatabaseName=dbname")
            .setSystem("microsoft.sql_server")
            .setHost("server_name")
            .setPort(1433)
            .setName("dbname")
            .build(),
        arg("jdbc:datadirect:oracle://server_name:1521;ServiceName=your_servicename")
            .setSystem("oracle.db")
            .setHost("server_name")
            .setPort(1521)
            .build(),
        arg("jdbc:datadirect:mysql://server_name:3306")
            .setSystem(MYSQL)
            .setHost("server_name")
            .setPort(3306)
            .build(),
        arg("jdbc:datadirect:postgresql://server_name:5432;DatabaseName=dbname")
            .setSystem(POSTGRESQL)
            .setHost("server_name")
            .setPort(5432)
            .setName("dbname")
            .build(),
        arg("jdbc:datadirect:db2://server_name:50000;DatabaseName=dbname")
            .setSystem("ibm.db2")
            .setHost("server_name")
            .setPort(50000)
            .setName("dbname")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("dataDirectArguments")
  void testDataDirectParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> tibcoArguments() {
    return args(
        // "the TIBCO JDBC drivers are based on the Progress DataDirect Connect drivers"
        // https://community.jaspersoft.com/documentation/tibco-jasperreports-server-administrator-guide/v601/working-data-sources
        arg("jdbc:tibcosoftware:sqlserver://server_name:1433;DatabaseName=dbname")
            .setSystem("microsoft.sql_server")
            .setHost("server_name")
            .setPort(1433)
            .setName("dbname")
            .build(),
        arg("jdbc:tibcosoftware:oracle://server_name:1521;ServiceName=your_servicename")
            .setSystem("oracle.db")
            .setHost("server_name")
            .setPort(1521)
            .build(),
        arg("jdbc:tibcosoftware:mysql://server_name:3306")
            .setSystem(MYSQL)
            .setHost("server_name")
            .setPort(3306)
            .build(),
        arg("jdbc:tibcosoftware:postgresql://server_name:5432;DatabaseName=dbname")
            .setSystem(POSTGRESQL)
            .setHost("server_name")
            .setPort(5432)
            .setName("dbname")
            .build(),
        arg("jdbc:tibcosoftware:db2://server_name:50000;DatabaseName=dbname")
            .setSystem("ibm.db2")
            .setHost("server_name")
            .setPort(50000)
            .setName("dbname")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("tibcoArguments")
  void testTibcoParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> secretsManagerArguments() {
    return args(
        // https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_jdbc.html
        arg("jdbc-secretsmanager:mysql://example.com:50000")
            .setSystem(MYSQL)
            .setHost("example.com")
            .setPort(50000)
            .build(),
        arg("jdbc-secretsmanager:postgresql://example.com:50000/dbname")
            .setSystem(POSTGRESQL)
            .setHost("example.com")
            .setPort(50000)
            .setName("dbname")
            .build(),
        arg("jdbc-secretsmanager:oracle:thin:@example.com:50000/ORCL")
            .setSystem("oracle.db")
            .setHost("example.com")
            .setPort(50000)
            .setName("orcl")
            .build(),
        arg("jdbc-secretsmanager:sqlserver://example.com:50000")
            .setSystem("microsoft.sql_server")
            .setHost("example.com")
            .setPort(50000)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("secretsManagerArguments")
  void testSecretsManagerParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> openTracingArguments() {
    return args(
        // https://github.com/opentracing-contrib/java-jdbc
        arg("jdbc:tracing:mysql://example.com:50000")
            .setSystem(MYSQL)
            .setHost("example.com")
            .setPort(50000)
            .build(),
        arg("jdbc:tracing:postgresql://example.com:50000/dbname")
            .setSystem(POSTGRESQL)
            .setHost("example.com")
            .setPort(50000)
            .setName("dbname")
            .build(),
        arg("jdbc:tracing:oracle:thin:@example.com:50000/ORCL")
            .setSystem("oracle.db")
            .setHost("example.com")
            .setPort(50000)
            .setName("orcl")
            .build(),
        arg("jdbc:tracing:sqlserver://example.com:50000")
            .setSystem("microsoft.sql_server")
            .setHost("example.com")
            .setPort(50000)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("openTracingArguments")
  void testOpenTracingParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> oceanbaseArguments() {
    return args(
        // https://en.oceanbase.com/
        arg("jdbc:oceanbase://host:3306/test")
            .setSystem("oceanbase")
            .setHost("host")
            .setPort(3306)
            .setName("test")
            .build(),
        arg("jdbc:oceanbase:oracle://host:1521")
            .setSystem("oracle.db")
            .setHost("host")
            .setPort(1521)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("oceanbaseArguments")
  void testOceasbaseParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> lindormArguments() {
    return args(
        // https://www.alibabacloud.com/help/en/lindorm/user-guide/view-endpoints
        arg("jdbc:lindorm:table:url=http://host:30060/test")
            .setSystem("lindorm")
            .setHost("host")
            .setName("test")
            .setPort(30060)
            .build(),
        arg("jdbc:lindorm:tsdb:url=http://host:8242/test")
            .setSystem("lindorm")
            .setHost("host")
            .setPort(8242)
            .setName("test")
            .build(),
        arg("jdbc:lindorm:search:url=http://host:30070/test")
            .setSystem("lindorm")
            .setHost("host")
            .setName("test")
            .setPort(30070)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("lindormArguments")
  void testLindormManagerParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> polardbArguments() {
    return argsWithDefaultPort(
        1521,
        arg("jdbc:polardb://example.com:1901")
            .setSystem("polardb")
            .setHost("example.com")
            .setPort(1901)
            .build(),
        arg("jdbc:polardb://example.com")
            .setSystem("polardb")
            .setHost("example.com")
            .setPort(1521)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("polardbArguments")
  void testPolardbParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> amazonAuroraArguments() {
    return argsWithDefaultPort(
        5432,
        // https://docs.aws.amazon.com/aurora-dsql/latest/userguide/SECTION_program-with-jdbc-connector.html
        arg("jdbc:aws-dsql:postgresql://your-cluster.dsql.us-east-1.on.aws/postgres")
            .setSystem(POSTGRESQL)
            .setHost("your-cluster.dsql.us-east-1.on.aws")
            .setPort(5432)
            .setName("postgres")
            .build(),
        arg("jdbc:aws-dsql:postgresql://your-cluster.dsql.us-east-1.on.aws:5432/postgres?user=admin")
            .setSystem(POSTGRESQL)
            .setHost("your-cluster.dsql.us-east-1.on.aws")
            .setPort(5432)
            .setNamespace("postgres|admin")
            .setName("postgres")
            .build(),
        // https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Connecting.html#Aurora.Connecting.JDBCDriverMySQL
        arg("jdbc:aws-wrapper:mysql://")
            .setSystem(MYSQL)
            .setHost("localhost")
            .setPort(3306)
            .setNoConfiguredTarget()
            .build(),
        arg("jdbc:aws-wrapper:mariadb://mdb.host:33/mdbdb?user=mdbuser&password=PW")
            .setSystem(MARIADB)
            .setHost("mdb.host")
            .setPort(33)
            .setName("mdbdb")
            .build(),
        // https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Connecting.html#Aurora.Connecting.JDBCDriverPostgreSQL
        arg("jdbc:aws-wrapper:postgresql://")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setNoConfiguredTarget()
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("amazonAuroraArguments")
  void testAmazonAuroraParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> sqliteArguments() {
    return args(
        arg("jdbc:sqlite:").setSystem("sqlite").build(),
        arg("jdbc:sqlite:memory:").setSystem("sqlite").build(),
        arg("jdbc:sqlite:file:mydb?mode=memory").setSystem("sqlite").setName("mydb").build(),
        arg("jdbc:sqlite:/tmp/app.db").setSystem("sqlite").setName("app.db").build(),
        arg("jdbc:sqlite:file:app.db").setSystem("sqlite").setName("app.db").build(),
        arg("jdbc:sqlite:resource:db").setSystem("sqlite").setName("db").build(),
        arg("jdbc:sqlite:resource:dir/db").setSystem("sqlite").setName("db").build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("sqliteArguments")
  void testSqliteParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> serverAddressGroupArguments() {
    return args(
        // https://jdbc.postgresql.org/documentation/use/#connection-fail-over
        arg("jdbc:postgresql://pg.host1:5432,pg.host2:5433/pgdb")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setName("pgdb")
            .setServerAddressGroup("pg.host1:5432,pg.host2:5433")
            .build(),
        arg("jdbc:postgresql://pg.host1,pg.host2/pgdb")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setName("pgdb")
            .setServerAddressGroup("pg.host1,pg.host2")
            .build(),
        // a bracketed ipv6 host list is not a legal registry-based authority either, so the uri
        // parse fails and the database name is not read
        arg("jdbc:postgresql://[2001:db8::1]:5432,[2001:db8::2]:5433/pgdb")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setServerAddressGroup("[2001:db8::1]:5432,[2001:db8::2]:5433")
            .build(),
        arg("jdbc:postgresql://pguser:pgpass@pg.host1:5432,pg.host2:5432/pgdb?ssl=true#frag")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setName("pgdb")
            .setServerAddressGroup("pg.host1,pg.host2")
            .build(),
        // the user info of a url shaped authority ends at its last '@', so a password that holds a
        // comma cannot leave a fragment of itself among the hosts
        arg("jdbc:postgresql://pguser:p,ss@pg.host1:5432,pg.host2:5433/pgdb")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setName("pgdb")
            .setServerAddressGroup("pg.host1:5432,pg.host2:5433")
            .build(),
        // a password that holds a parenthesis makes the authority look like an address block, and
        // an entry that still carries an '@' is dropped rather than reported
        arg("jdbc:postgresql://pguser:p(x)y@pg.host1:5432,pg.host2:5433/pgdb")
            .setSystem(POSTGRESQL)
            .setHost("localhost")
            .setPort(5432)
            .setName("pgdb")
            .setMultiTarget()
            .build(),
        // https://dev.mysql.com/doc/connector-j/en/connector-j-multi-host-connections.html
        arg("jdbc:mysql://mysql.host1:3306,mysql.host2:3307/mydb")
            .setSystem(MYSQL)
            .setHost("localhost")
            .setPort(3306)
            .setName("mydb")
            .setServerAddressGroup("mysql.host1:3306,mysql.host2:3307")
            .build(),
        // an address block spells its credentials out as attributes, so a password may hold an '@'
        // and characters that look like delimiters; none of it may reach the group target
        arg("jdbc:mysql:replication://address=(host=mdb.host1)(port=33)(user=mdbuser)"
                + "(password=p@ss,w0rd),address=(host=mdb.host2)(port=3306)/mdbdb")
            .setSystem(MYSQL)
            .setHost("mdb.host1")
            .setPort(33)
            .setName("mdbdb")
            .setServerAddressGroup("mdb.host1:33,mdb.host2:3306")
            .build(),
        // https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties
        arg("jdbc:sqlserver://ss.host1:1433;databaseName=ssdb;failoverPartner=ss.host2")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host1")
            .setPort(1433)
            .setName("ssdb")
            .setServerAddressGroup("ss.host1,ss.host2")
            .build(),
        arg("jdbc:sqlserver://ss.host1\\instance1:1433;databaseName=ssdb;"
                + "failoverPartner=ss.host2\\instance2")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host1")
            .setPort(1433)
            .setNamespace("instance1|ssdb")
            .setName("instance1")
            .setServerAddressGroup("ss.host1,ss.host2\\instance2")
            .build(),
        arg("jdbc:sqlserver://ss.host1;instanceName=instance1;failoverPartner=ss.host2")
            .setSystem("microsoft.sql_server")
            .setHost("ss.host1")
            .setPort(1433)
            .setServerAddressGroup("ss.host1\\instance1,ss.host2")
            .build(),
        arg("jdbc:sqlserver://[2001:db8::1]:1433;failoverPartner=2001:db8::2")
            .setSystem("microsoft.sql_server")
            .setHost("2001:db8::1")
            .setPort(1433)
            .setServerAddressGroup("2001:db8::1,2001:db8::2")
            .build(),
        // an ADDRESS_LIST is optional, a DESCRIPTION may hold the addresses directly
        arg("jdbc:oracle:thin:@(description=(address=(protocol=tcp)(host=orcl.host1)(port=1521))"
                + "(address=(protocol=tcp)(host=orcl.host2)(port=1522))"
                + "(connect_data=(service_name=orclsn)))")
            .setSystem("oracle.db")
            .setHost("orcl.host1")
            .setPort(1521)
            .setName("orclsn")
            .setServerAddressGroup("orcl.host1:1521,orcl.host2:1522")
            .build(),
        // flattening a DESCRIPTION_LIST would lose each DESCRIPTION's CONNECT_DATA and options
        arg("jdbc:oracle:thin:@(description_list="
                + "(description=(address=(protocol=tcp)(host=orcl.host1)(port=1521))"
                + "(connect_data=(service_name=orclsn)))"
                + "(description=(address=(protocol=tcp)(host=orcl.host2)(port=1522))"
                + "(connect_data=(service_name=orclsn))))")
            .setSystem("oracle.db")
            .setHost("orcl.host1")
            .setPort(1521)
            .setName("orclsn")
            .setMultiTarget()
            .build(),
        arg("jdbc:oracle:thin:@(description=(source_route=on)"
                + "(address=(protocol=tcp)(host=cman.host)(port=1630))"
                + "(address=(protocol=tcp)(host=orcl.host)(port=1521))"
                + "(connect_data=(service_name=orclsn)))")
            .setSystem("oracle.db")
            .setHost("cman.host")
            .setPort(1630)
            .setName("orclsn")
            .setMultiTarget()
            .build(),
        arg("jdbc:oracle:thin:@//cman.host:1630,orcl.host:1521/orclsn?source_route=on")
            .setSystem("oracle.db")
            .setHost("cman.host")
            .setPort(1630)
            .setName("orclsn")
            .setMultiTarget()
            .build(),
        arg("jdbc:mariadb:failover://mdb.host:3306/mdbdb")
            .setSystem(MARIADB)
            .setHost("mdb.host")
            .setPort(3306)
            .setConfiguredServerTarget("mdb.host", null)
            .setName("mdbdb")
            .build(),
        // a single address block is not a group either, and its password stays out of every field
        arg("jdbc:mariadb:failover://address=(host=mdb.host)(port=3306)(user=mdbuser)"
                + "(password=p@ss,w0rd)/mdbdb")
            .setSystem(MARIADB)
            .setHost("mdb.host")
            .setPort(3306)
            .setConfiguredServerTarget("mdb.host", null)
            .setName("mdbdb")
            .build());
  }

  private static Stream<Arguments> configuredOrderServerAddressGroupArguments() {
    return Stream.of(
        argumentSet(
            "reversed PostgreSQL targets with a duplicate",
            "jdbc:postgresql://pg.host2:5433,pg.host1:5432,pg.host2:5433/pgdb",
            "pg.host2:5433,pg.host1:5432,pg.host2:5433"),
        argumentSet(
            "PostgreSQL target-server selection order",
            "jdbc:postgresql://preferred.host,fallback.host/pgdb?targetServerType=primary",
            "preferred.host,fallback.host"),
        argumentSet(
            "MariaDB failover targets",
            "jdbc:mariadb:failover://primary.host:3306,secondary.host:3307/mdbdb",
            "primary.host:3306,secondary.host:3307"),
        argumentSet(
            "reversed MariaDB sequential targets",
            "jdbc:mariadb:sequential://mdb.host2:3307,mdb.host1:3306/mdbdb",
            "mdb.host2:3307,mdb.host1:3306"),
        argumentSet(
            "MariaDB replication targets",
            "jdbc:mariadb:replication://primary.host:3306,replica.host:3307/mdbdb",
            "primary.host:3306,replica.host:3307"),
        argumentSet(
            "MySQL configured replication roles",
            "jdbc:mysql:replication://address=(host=replica.host)(port=3307)(type=REPLICA),"
                + "address=(host=source.host)(port=3306)(type=SOURCE)/mydb",
            "replica.host:3307,source.host:3306"),
        argumentSet(
            "SQL Server principal and failover partner order",
            "jdbc:sqlserver://principal.host:1433;failoverPartner=partner.host:1444",
            "principal.host:1433,partner.host:1444"),
        argumentSet(
            "SQL Server IPv6 ports",
            "jdbc:sqlserver://[2001:db8::1]:1433;failoverPartner=[2001:db8::2]:1444",
            "[2001:db8::1]:1433,[2001:db8::2]:1444"),
        argumentSet(
            "Oracle address list order with a duplicate",
            "jdbc:oracle:thin:@(description=(address_list="
                + "(address=(protocol=tcp)(host=orcl.host2)(port=1522))"
                + "(address=(protocol=tcp)(host=orcl.host1)(port=1521))"
                + "(address=(protocol=tcp)(host=orcl.host2)(port=1522)))"
                + "(connect_data=(service_name=orclsn)))",
            "orcl.host2:1522,orcl.host1:1521,orcl.host2:1522"));
  }

  private static Stream<Arguments> normalizedServerAddressGroupArguments() {
    return Stream.of(
        argumentSet(
            "PostgreSQL shared non-default port",
            "jdbc:postgresql://pg.host1:15432,pg.host2:15432/pgdb",
            "pg.host1:15432,pg.host2:15432"),
        argumentSet(
            "PostgreSQL default ports",
            "jdbc:postgresql://pg.host1,pg.host2:5432/pgdb",
            "pg.host1,pg.host2"),
        argumentSet(
            "PostgreSQL IPv6 default ports",
            "jdbc:postgresql://[2001:db8::1],[2001:db8::2]/pgdb",
            "2001:db8::1,2001:db8::2"),
        argumentSet(
            "PostgreSQL mixed IPv6 ports",
            "jdbc:postgresql://[2001:db8::1],[2001:db8::2]:15432/pgdb",
            "[2001:db8::1]:5432,[2001:db8::2]:15432"),
        argumentSet(
            "MySQL address blocks on default ports",
            "jdbc:mysql:replication://address=(host=source.host)(port=3306),"
                + "address=(host=replica.host)/mydb",
            "source.host,replica.host"),
        argumentSet(
            "MariaDB shared non-default port",
            "jdbc:mariadb:failover://mdb.host1:13306,mdb.host2:13306/mdbdb",
            "mdb.host1:13306,mdb.host2:13306"),
        argumentSet(
            "SQL Server shared non-default port",
            "jdbc:sqlserver://primary.host:1444;failoverPartner=partner.host:1444",
            "primary.host:1444,partner.host:1444"),
        argumentSet(
            "SQL Server mixed port and named instance",
            "jdbc:sqlserver://primary.host:1444;failoverPartner=partner.host\\instance",
            "primary.host:1444,partner.host\\instance"),
        argumentSet(
            "Oracle shared non-default port",
            "jdbc:oracle:thin:@(description="
                + "(address=(protocol=tcp)(host=orcl.host1)(port=2521))"
                + "(address=(protocol=tcp)(host=orcl.host2)(port=2521))"
                + "(connect_data=(service_name=orclsn)))",
            "orcl.host1:2521,orcl.host2:2521"),
        argumentSet(
            "Oracle Easy Connect default ports",
            "jdbc:oracle:thin:@//orcl.host1,orcl.host2/orclsn",
            "orcl.host1,orcl.host2"),
        argumentSet(
            "Oracle Easy Connect shared non-default port",
            "jdbc:oracle:thin:@tcps://orcl.host1:2521,orcl.host2:2521/orclsn",
            "orcl.host1:2521,orcl.host2:2521"),
        argumentSet(
            "Oracle Easy Connect mixed IPv6 ports",
            "jdbc:oracle:thin:@//[2001:db8::1],[2001:db8::2]:2521/orclsn",
            "[2001:db8::1]:1521,[2001:db8::2]:2521"),
        argumentSet(
            "unknown database default keeps explicit ports",
            "jdbc:unknown://unknown.host1:1234,unknown.host2:1234/db",
            "unknown.host1:1234,unknown.host2:1234"),
        argumentSet(
            "unknown database address blocks become endpoints",
            "jdbc:unknown://address=(host=unknown.host1)(port=1234),"
                + "address=(host=unknown.host2)(port=1234)/db",
            "unknown.host1:1234,unknown.host2:1234"),
        argumentSet(
            "PolarDB default ports",
            "jdbc:polardb://polardb.host1:1521,polardb.host2/db",
            "polardb.host1,polardb.host2"));
  }

  @ParameterizedTest
  @MethodSource("normalizedServerAddressGroupArguments")
  void normalizesServerAddressGroupPorts(String url, String expectedServerAddressGroup) {
    DbInfo dbInfo = parse(url, null);

    assertThat(dbInfo.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create(expectedServerAddressGroup, null));
  }

  private static Stream<Arguments> limitedServerAddressGroupArguments() {
    return Stream.of(
        argumentSet(
            "exactly five PostgreSQL endpoints",
            "jdbc:postgresql://h1,h2,h3,h4,h5/db",
            "h1,h2,h3,h4,h5"),
        argumentSet(
            "six PostgreSQL endpoints", "jdbc:postgresql://h1,h2,h3,h4,h5,h6/db", "h1,h2,h3,h4,h5"),
        argumentSet(
            "non-default MariaDB port after the fifth endpoint",
            "jdbc:mariadb:failover://h1,h2,h3,h4,h5,h6:13306/db",
            "h1:3306,h2:3306,h3:3306,h4:3306,h5:3306"),
        argumentSet(
            "six Oracle address blocks",
            "jdbc:oracle:thin:@(description=(address_list="
                + "(address=(host=h1)(port=1521))"
                + "(address=(host=h2)(port=1521))"
                + "(address=(host=h3)(port=1521))"
                + "(address=(host=h4)(port=1521))"
                + "(address=(host=h5)(port=1521))"
                + "(address=(host=h6)(port=1521)))"
                + "(connect_data=(service_name=orclsn)))",
            "h1,h2,h3,h4,h5"));
  }

  @ParameterizedTest
  @MethodSource("limitedServerAddressGroupArguments")
  void limitsServerAddressGroupAfterInspectingTheCompleteList(
      String url, String expectedServerAddressGroup) {
    assertThat(parse(url, null).getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create(expectedServerAddressGroup, null));
  }

  @Test
  void invalidEndpointAfterTheFifthEndpointFailsClosed() {
    DbInfo dbInfo = parse("jdbc:postgresql://h1,h2,h3,h4,h5,unexpected=value/db", null);

    assertThat(dbInfo.getConfiguredServerTarget()).isNull();
  }

  @Test
  void invalidUnixSocketInServerAddressGroupFailsClosed() {
    assertThat(parseServerTargetGroup("/valid.sock,/invalid?sock", null)).isNull();
  }

  @Test
  void invalidExplicitPortsInServerAddressGroupFailClosed() {
    DbInfo dbInfo = parse("jdbc:unknown://h1:70000,h2:70000/db", null);

    assertThat(dbInfo.getConfiguredServerTarget()).isNull();
  }

  @ParameterizedTest
  @MethodSource("configuredOrderServerAddressGroupArguments")
  void preservesConfiguredServerAddressGroupOrder(String url, String expectedServerAddressGroup) {
    assertThat(parse(url, null).getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create(expectedServerAddressGroup, null));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("serverAddressGroupArguments")
  void testServerAddressGroupParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "postgresql://user:123,a/ss@pg.host:5432",
        "postgresql://user:p,a/ss@pg.host:5432/db",
        "postgresql://user:p,a/ss@pg.host:5432",
        "postgresql://user:pa,ss/word@pg.host1:5432,pg.host2:5433/pgdb",
        "postgresql://user:p@ss,w/ord@pg.host1:5432,pg.host2:5433/pgdb"
      })
  void testAmbiguousUserInfoIsNotExtractedAsAuthority(String url) {
    assertThat(extractAuthority(url)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"postgresql://h1,h2/db/admin@corp.com", "postgresql://h1,h2/db#admin@corp.com"})
  void testAtAfterCommaSeparatedAuthorityIsAmbiguous(String url) {
    assertThat(extractAuthority(url)).isNull();
  }

  @Test
  void testAtInQueryParameterDoesNotHideAuthority() {
    assertThat(extractAuthority("postgresql://h1,h2/db?user=admin@corp.com")).isEqualTo("h1,h2");
  }

  private static void testVerifySystemSubtypeParsingOfUrl(ParseTestArgument argument) {
    DbInfo info = parse(argument.url, argument.properties);
    DbInfo expected = argument.dbInfo;
    assertThat(info.getDbSystemName()).isEqualTo(expected.getDbSystemName());
    assertThat(info.getConfiguredServerTarget()).isEqualTo(expected.getConfiguredServerTarget());
    assertThat(info.getDbNamespace()).isEqualTo(expected.getDbNamespace());
    assertThat(info).isEqualTo(expected);
  }

  static class ParseTestArgument {
    final String url;
    final Properties properties;
    final DbInfo dbInfo;

    ParseTestArgument(ParseTestArgumentBuilder builder) {
      this.url = builder.url;
      this.properties = builder.properties;

      String namespace = builder.namespace != null ? builder.namespace : builder.name;

      this.dbInfo =
          DbInfo.builder()
              .dbSystemName(builder.system)
              .dbNamespace(namespace)
              .configuredServerTarget(
                  builder.noConfiguredTarget || builder.multiTarget
                      ? null
                      : builder.configuredServerTarget != null
                          ? builder.configuredServerTarget
                          : builder.serverAddressGroup == null && builder.host == null
                              ? null
                              : builder.serverAddressGroup != null
                                  ? DbServerTarget.create(builder.serverAddressGroup, null)
                                  : DbServerTarget.create(builder.host, builder.port))
              .build();
    }

    private ParseTestArgument(String url, Properties properties, DbInfo dbInfo) {
      this.url = url;
      this.properties = properties;
      this.dbInfo = dbInfo;
    }

    private ParseTestArgument withoutConfiguredDefaultPort(int defaultPort) {
      DbServerTarget target = dbInfo.getConfiguredServerTarget();
      if (target == null || !Integer.valueOf(defaultPort).equals(target.getPort())) {
        return this;
      }
      return new ParseTestArgument(
          url,
          properties,
          dbInfo.toBuilder()
              .configuredServerTarget(DbServerTarget.create(target.getAddress(), null))
              .build());
    }

    @Override
    public String toString() {
      return dbInfo.getDbSystemName() + " parsing of " + url;
    }
  }

  static class ParseTestArgumentBuilder {
    String url;
    Properties properties;
    String system;
    String host;
    Integer port;
    String namespace;
    String name;
    String serverAddressGroup;
    DbServerTarget configuredServerTarget;
    boolean multiTarget;
    boolean noConfiguredTarget;

    ParseTestArgumentBuilder(String url) {
      this.url = url;
    }

    ParseTestArgumentBuilder setProperties(Properties properties) {
      this.properties = properties;
      return this;
    }

    ParseTestArgumentBuilder setSystem(String system) {
      this.system = system;
      return this;
    }

    ParseTestArgumentBuilder setHost(String host) {
      this.host = host;
      return this;
    }

    ParseTestArgumentBuilder setPort(Integer port) {
      this.port = port;
      return this;
    }

    ParseTestArgumentBuilder setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    ParseTestArgumentBuilder setName(String name) {
      this.name = name;
      return this;
    }

    ParseTestArgumentBuilder setServerAddressGroup(String serverAddressGroup) {
      this.serverAddressGroup = serverAddressGroup;
      return this;
    }

    ParseTestArgumentBuilder setConfiguredServerTarget(String address, Integer port) {
      this.configuredServerTarget = DbServerTarget.create(address, port);
      return this;
    }

    ParseTestArgumentBuilder setMultiTarget() {
      this.multiTarget = true;
      return this;
    }

    ParseTestArgumentBuilder setNoConfiguredTarget() {
      this.noConfiguredTarget = true;
      return this;
    }

    ParseTestArgument build() {
      return new ParseTestArgument(this);
    }
  }

  private static ParseTestArgumentBuilder arg(String url) {
    return new ParseTestArgumentBuilder(url);
  }

  static Stream<Arguments> args(ParseTestArgument... testArguments) {
    List<Arguments> list = new ArrayList<>();
    for (ParseTestArgument arg : testArguments) {
      list.add(arguments(arg));
    }
    return list.stream();
  }

  private static Stream<Arguments> argsWithDefaultPort(
      int defaultPort, ParseTestArgument... testArguments) {
    List<Arguments> list = new ArrayList<>();
    for (ParseTestArgument arg : testArguments) {
      list.add(arguments(arg.withoutConfiguredDefaultPort(defaultPort)));
    }
    return list.stream();
  }
}
