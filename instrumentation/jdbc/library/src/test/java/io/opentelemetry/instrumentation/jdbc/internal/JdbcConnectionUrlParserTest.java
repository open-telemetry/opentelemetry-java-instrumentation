/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser.parse;
import static io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

  @ParameterizedTest
  @ValueSource(strings = {"", "jdbc:", "jdbc::", "bogus:string"})
  void testInvalidUrlReturnsDefault(String url) {
    assertThat(JdbcConnectionUrlParser.parse(url, null)).isEqualTo(DEFAULT);
  }

  @Test
  void testNullUrlReturnsDefault() {
    assertThat(JdbcConnectionUrlParser.parse(null, null)).isEqualTo(DEFAULT);
  }

  private static Stream<Arguments> mySqlArguments() {
    return args(
        // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
        // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
        arg("jdbc:mysql:///")
            .setShortUrl("mysql://localhost:3306")
            .setSystem("mysql")
            .setHost("localhost")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql:///")
            .setProperties(stdProps())
            .setShortUrl("mysql://stdServerName:9999")
            .setSystem("mysql")
            .setUser("stdUserName")
            .setHost("stdServerName")
            .setPort(9999)
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:mysql://my.host")
            .setShortUrl("mysql://my.host:3306")
            .setSystem("mysql")
            .setHost("my.host")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql://my.host?user=myuser&password=PW")
            .setShortUrl("mysql://my.host:3306")
            .setSystem("mysql")
            .setUser("myuser")
            .setHost("my.host")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql://my.host:22/mydb?user=myuser&password=PW")
            .setShortUrl("mysql://my.host:22")
            .setSystem("mysql")
            .setUser("myuser")
            .setHost("my.host")
            .setPort(22)
            .setDb("mydb")
            .build(),
        arg("jdbc:mysql://127.0.0.1:22/mydb?user=myuser&password=PW")
            .setProperties(stdProps())
            .setShortUrl("mysql://127.0.0.1:22")
            .setSystem("mysql")
            .setUser("myuser")
            .setHost("127.0.0.1")
            .setPort(22)
            .setDb("mydb")
            .build(),
        arg("jdbc:mysql://myuser:password@my.host:22/mydb")
            .setShortUrl("mysql://my.host:22")
            .setSystem("mysql")
            .setUser("myuser")
            .setHost("my.host")
            .setPort(22)
            .setDb("mydb")
            .build(),
        arg("jdbc:mysql:aurora://mdb.host/mdbdb")
            .setShortUrl("mysql:aurora://mdb.host:3306")
            .setSystem("mysql")
            .setSubtype("aurora")
            .setHost("mdb.host")
            .setPort(3306)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mysql:failover://localhost/mdbdb?autoReconnect=true")
            .setShortUrl("mysql:failover://localhost:3306")
            .setSystem("mysql")
            .setSubtype("failover")
            .setHost("localhost")
            .setPort(3306)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mysql:loadbalance://127.0.0.1,127.0.0.1:3306/mdbdb?user=mdbuser&password=PW")
            .setShortUrl("mysql:loadbalance://127.0.0.1:3306")
            .setSystem("mysql")
            .setSubtype("loadbalance")
            .setUser("mdbuser")
            .setHost("127.0.0.1")
            .setPort(3306)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mysql:replication://address=(HOST=127.0.0.1)(port=33)(user=mdbuser)(password=PW),address=(host=mdb.host)(port=3306)(user=otheruser)(password=PW)/mdbdb?user=wrong&password=PW")
            .setShortUrl("mysql:replication://127.0.0.1:33")
            .setSystem("mysql")
            .setSubtype("replication")
            .setUser("mdbuser")
            .setHost("127.0.0.1")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mysql:replication://address=(HOST=mdb.host),address=(host=anotherhost)(port=3306)(user=wrong)(password=PW)/mdbdb?user=mdbuser&password=PW")
            .setShortUrl("mysql:replication://mdb.host:3306")
            .setSystem("mysql")
            .setSubtype("replication")
            .setUser("mdbuser")
            .setHost("mdb.host")
            .setPort(3306)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mysql:loadbalance://localhost")
            .setShortUrl("mysql:loadbalance://localhost:3306")
            .setSystem("mysql")
            .setSubtype("loadbalance")
            .setHost("localhost")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql:loadbalance://host:3306") // with port but no slash
            .setShortUrl("mysql:loadbalance://host:3306")
            .setSystem("mysql")
            .setSubtype("loadbalance")
            .setHost("host")
            .setPort(3306)
            .build(),
        arg("jdbc:mysql:failover://[::1]:3306") // IPv6 without slash
            .setShortUrl("mysql:failover://::1:3306")
            .setSystem("mysql")
            .setSubtype("failover")
            .setHost("::1")
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
            .setShortUrl("clickhouse:http://localhost:8123")
            .setSystem("clickhouse")
            .setSubtype("http")
            .setHost("localhost")
            .setPort(8123)
            .setDb("mydb")
            .build(),
        arg("jdbc:clickhouse:https://localhost:8443?ssl=true")
            .setShortUrl("clickhouse:https://localhost:8443")
            .setSystem("clickhouse")
            .setSubtype("https")
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
    return args(
        // https://jdbc.postgresql.org/documentation/94/connect.html
        arg("jdbc:postgresql:///")
            .setShortUrl("postgresql://localhost:5432")
            .setSystem("postgresql")
            .setHost("localhost")
            .setPort(5432)
            .build(),
        arg("jdbc:postgresql:///")
            .setProperties(stdProps())
            .setShortUrl("postgresql://stdServerName:9999")
            .setSystem("postgresql")
            .setUser("stdUserName")
            .setHost("stdServerName")
            .setPort(9999)
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:postgresql://pg.host")
            .setShortUrl("postgresql://pg.host:5432")
            .setSystem("postgresql")
            .setHost("pg.host")
            .setPort(5432)
            .build(),
        arg("jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW")
            .setShortUrl("postgresql://pg.host:11")
            .setSystem("postgresql")
            .setUser("pguser")
            .setHost("pg.host")
            .setPort(11)
            .setDb("pgdb")
            .build(),
        arg("jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW")
            .setProperties(stdProps())
            .setShortUrl("postgresql://pg.host:11")
            .setSystem("postgresql")
            .setUser("pguser")
            .setHost("pg.host")
            .setPort(11)
            .setDb("pgdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("postgresArguments")
  void testPostgresParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> mariaDbArguments() {
    return args(
        // https://mariadb.com/kb/en/library/about-mariadb-connector-j/#connection-strings
        arg("jdbc:mariadb:127.0.0.1:33/mdbdb")
            .setShortUrl("mariadb://127.0.0.1:33")
            .setSystem("mariadb")
            .setHost("127.0.0.1")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:localhost/mdbdb")
            .setShortUrl("mariadb://localhost:3306")
            .setSystem("mariadb")
            .setHost("localhost")
            .setPort(3306)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:localhost/mdbdb?user=mdbuser&password=PW")
            .setProperties(stdProps())
            .setShortUrl("mariadb://localhost:9999")
            .setSystem("mariadb")
            .setUser("mdbuser")
            .setHost("localhost")
            .setPort(9999)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:localhost:33/mdbdb")
            .setProperties(stdProps())
            .setShortUrl("mariadb://localhost:33")
            .setSystem("mariadb")
            .setUser("stdUserName")
            .setHost("localhost")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb://mdb.host:33/mdbdb?user=mdbuser&password=PW")
            .setShortUrl("mariadb://mdb.host:33")
            .setSystem("mariadb")
            .setUser("mdbuser")
            .setHost("mdb.host")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:aurora://mdb.host/mdbdb")
            .setShortUrl("mariadb:aurora://mdb.host:3306")
            .setSystem("mariadb")
            .setSubtype("aurora")
            .setHost("mdb.host")
            .setPort(3306)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:failover://mdb.host1:33,mdb.host/mdbdb?characterEncoding=utf8")
            .setShortUrl("mariadb:failover://mdb.host1:33")
            .setSystem("mariadb")
            .setSubtype("failover")
            .setHost("mdb.host1")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:sequential://mdb.host1,mdb.host2:33/mdbdb")
            .setShortUrl("mariadb:sequential://mdb.host1:3306")
            .setSystem("mariadb")
            .setSubtype("sequential")
            .setHost("mdb.host1")
            .setPort(3306)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:loadbalance://127.0.0.1:33,mdb.host/mdbdb")
            .setShortUrl("mariadb:loadbalance://127.0.0.1:33")
            .setSystem("mariadb")
            .setSubtype("loadbalance")
            .setHost("127.0.0.1")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:loadbalance://127.0.0.1:33/mdbdb")
            .setShortUrl("mariadb:loadbalance://127.0.0.1:33")
            .setSystem("mariadb")
            .setSubtype("loadbalance")
            .setHost("127.0.0.1")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:loadbalance://[2001:0660:7401:0200:0000:0000:0edf:bdd7]:33,mdb.host/mdbdb")
            .setShortUrl("mariadb:loadbalance://2001:0660:7401:0200:0000:0000:0edf:bdd7:33")
            .setSystem("mariadb")
            .setSubtype("loadbalance")
            .setHost("2001:0660:7401:0200:0000:0000:0edf:bdd7")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:replication://localhost:33,anotherhost:3306/mdbdb")
            .setShortUrl("mariadb:replication://localhost:33")
            .setSystem("mariadb")
            .setSubtype("replication")
            .setHost("localhost")
            .setPort(33)
            .setDb("mdbdb")
            .build(),
        arg("jdbc:mariadb:loadbalance://localhost")
            .setShortUrl("mariadb:loadbalance://localhost:3306")
            .setSystem("mariadb")
            .setSubtype("loadbalance")
            .setHost("localhost")
            .setPort(3306)
            .build(),
        arg("jdbc:mariadb:loadbalance://host:3306") // with port but no slash
            .setShortUrl("mariadb:loadbalance://host:3306")
            .setSystem("mariadb")
            .setSubtype("loadbalance")
            .setHost("host")
            .setPort(3306)
            .build(),
        arg("jdbc:mariadb:failover://[::1]:3306") // IPv6 without slash
            .setShortUrl("mariadb:failover://::1:3306")
            .setSystem("mariadb")
            .setSubtype("failover")
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
    return args(
        // https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
        arg("jdbc:microsoft:sqlserver://;")
            .setShortUrl("microsoft:sqlserver://localhost:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setHost("localhost")
            .setPort(1433)
            .build(),
        arg("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
            .setShortUrl("sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:1433")
            .setSystem("mssql")
            .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
            .setPort(1433)
            .build(),
        arg("jdbc:sqlserver://;serverName=2001:0db8:85a3:0000:0000:8a2e:0370:7334")
            .setShortUrl("sqlserver://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:1433")
            .setSystem("mssql")
            .setHost("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]")
            .setPort(1433)
            .build(),
        arg("jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43")
            .setShortUrl("sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43")
            .setSystem("mssql")
            .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
            .setPort(43)
            .build(),
        arg("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance")
            .setShortUrl("sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:1433")
            .setSystem("mssql")
            .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
            .setPort(1433)
            .setName("ssinstance")
            .build(),
        arg("jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance]:43")
            .setShortUrl("sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43")
            .setSystem("mssql")
            .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
            .setPort(43)
            .setName("ssinstance")
            .build(),
        arg("jdbc:microsoft:sqlserver://;")
            .setProperties(stdProps())
            .setShortUrl("microsoft:sqlserver://stdServerName:9999")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setUser("stdUserName")
            .setHost("stdServerName")
            .setPort(9999)
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:sqlserver://ss.host\\ssinstance:44;databaseName=ssdb;user=ssuser;password=pw")
            .setShortUrl("sqlserver://ss.host:44")
            .setSystem("mssql")
            .setUser("ssuser")
            .setHost("ss.host")
            .setPort(44)
            .setName("ssinstance")
            .setDb("ssdb")
            .build(),
        arg("jdbc:sqlserver://;serverName=ss.host\\ssinstance:44;DatabaseName=;")
            .setShortUrl("sqlserver://ss.host:44")
            .setSystem("mssql")
            .setHost("ss.host")
            .setPort(44)
            .setName("ssinstance")
            .build(),
        arg("jdbc:sqlserver://ss.host;serverName=althost;DatabaseName=ssdb;")
            .setShortUrl("sqlserver://ss.host:1433")
            .setSystem("mssql")
            .setHost("ss.host")
            .setPort(1433)
            .setDb("ssdb")
            .build(),
        arg("jdbc:microsoft:sqlserver://ss.host:44;DatabaseName=ssdb;user=ssuser;password=pw;user=ssuser2;")
            .setShortUrl("microsoft:sqlserver://ss.host:44")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setUser("ssuser")
            .setHost("ss.host")
            .setPort(44)
            .setDb("ssdb")
            .build(),

        // http://jtds.sourceforge.net/faq.html#urlFormat
        arg("jdbc:jtds:sqlserver://ss.host/ssdb")
            .setShortUrl("jtds:sqlserver://ss.host:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setHost("ss.host")
            .setPort(1433)
            .setDb("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb")
            .setShortUrl("jtds:sqlserver://ss.host:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setHost("ss.host")
            .setPort(1433)
            .setDb("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb;user=ssuser")
            .setShortUrl("jtds:sqlserver://ss.host:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setUser("ssuser")
            .setHost("ss.host")
            .setPort(1433)
            .setDb("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host/ssdb;instance=ssinstance")
            .setShortUrl("jtds:sqlserver://ss.host:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setHost("ss.host")
            .setPort(1433)
            .setName("ssinstance")
            .setDb("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1444/ssdb;instance=ssinstance")
            .setShortUrl("jtds:sqlserver://ss.host:1444")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setHost("ss.host")
            .setPort(1444)
            .setName("ssinstance")
            .setDb("ssdb")
            .build(),
        arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb;instance=ssinstance;user=ssuser")
            .setShortUrl("jtds:sqlserver://ss.host:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setUser("ssuser")
            .setHost("ss.host")
            .setPort(1433)
            .setName("ssinstance")
            .setDb("ssdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("sqlServerArguments")
  void testSqlServerParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> oracleArguments() {
    return args(
        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/urls.htm
        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/jdbcthin.htm
        arg("jdbc:oracle:thin:orcluser/PW@localhost:55:orclsn")
            .setShortUrl("oracle:thin://localhost:55")
            .setSystem("oracle")
            .setSubtype("thin")
            .setUser("orcluser")
            .setHost("localhost")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcluser/PW@//orcl.host:55/orclsn")
            .setShortUrl("oracle:thin://orcl.host:55")
            .setSystem("oracle")
            .setSubtype("thin")
            .setUser("orcluser")
            .setHost("orcl.host")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcluser/PW@127.0.0.1:orclsn")
            .setShortUrl("oracle:thin://127.0.0.1:1521")
            .setSystem("oracle")
            .setSubtype("thin")
            .setUser("orcluser")
            .setHost("127.0.0.1")
            .setPort(1521) // Default Oracle port assumed as not specified in the URL
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcluser/PW@//orcl.host/orclsn")
            .setShortUrl("oracle:thin://orcl.host:1521")
            .setSystem("oracle")
            .setSubtype("thin")
            .setUser("orcluser")
            .setHost("orcl.host")
            .setPort(1521) // Default Oracle port assumed as not specified in the URL
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:@//orcl.host:55/orclsn")
            .setShortUrl("oracle:thin://orcl.host:55")
            .setSystem("oracle")
            .setSubtype("thin")
            .setHost("orcl.host")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:@ldap://orcl.host:55/some,cn=OracleContext,dc=com")
            .setShortUrl("oracle:thin://orcl.host:55")
            .setSystem("oracle")
            .setSubtype("thin")
            .setHost("orcl.host")
            .setPort(55)
            .setName("some,cn=oraclecontext,dc=com")
            .build(),
        arg("jdbc:oracle:thin:127.0.0.1:orclsn")
            .setShortUrl("oracle:thin://127.0.0.1:1521")
            .setSystem("oracle")
            .setSubtype("thin")
            .setHost("127.0.0.1")
            .setPort(1521) // Default Oracle port assumed as not specified in the URL
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:orcl.host:orclsn")
            .setProperties(stdProps())
            .setShortUrl("oracle:thin://orcl.host:9999")
            .setSystem("oracle")
            .setSubtype("thin")
            .setUser("stdUserName")
            .setHost("orcl.host")
            .setPort(9999)
            .setName("orclsn")
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=127.0.0.1)(PORT=666))"
                + "(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=orclsn)))")
            .setShortUrl("oracle:thin://127.0.0.1:666")
            .setSystem("oracle")
            .setSubtype("thin")
            .setHost("127.0.0.1")
            .setPort(666)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:thin:@ ( description = (connect_timeout=90)(retry_count=20)(retry_delay=3) (transport_connect_timeout=3000) (address_list = (load_balance = on) (failover = on) (address = (protocol = tcp)(host = orcl.host1 )(port = 1521 )) (address = (protocol = tcp)(host = orcl.host2)(port = 1521)) (address = (protocol = tcp)(host = orcl.host3)(port = 1521)) (address = (protocol = tcp)(host = orcl.host4)(port = 1521)) ) (connect_data = (server = dedicated) (service_name = orclsn)))")
            .setShortUrl("oracle:thin://orcl.host1:1521")
            .setSystem("oracle")
            .setSubtype("thin")
            .setHost("orcl.host1")
            .setPort(1521)
            .setName("orclsn")
            .build(),

        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/instclnt.htm
        arg("jdbc:oracle:drivertype:orcluser/PW@orcl.host:55/orclsn")
            .setShortUrl("oracle:drivertype://orcl.host:55")
            .setSystem("oracle")
            .setSubtype("drivertype")
            .setUser("orcluser")
            .setHost("orcl.host")
            .setPort(55)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:oci8:@")
            .setShortUrl("oracle:oci8:")
            .setSystem("oracle")
            .setSubtype("oci8")
            .setPort(1521)
            .build(),
        arg("jdbc:oracle:oci8:@")
            .setProperties(stdProps())
            .setShortUrl("oracle:oci8://stdServerName:9999")
            .setSystem("oracle")
            .setSubtype("oci8")
            .setUser("stdUserName")
            .setHost("stdServerName")
            .setPort(9999)
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:oracle:oci8:@orclsn")
            .setShortUrl("oracle:oci8:")
            .setSystem("oracle")
            .setSubtype("oci8")
            .setPort(1521)
            .setName("orclsn")
            .build(),
        arg("jdbc:oracle:oci:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=orcl.host)(PORT=55))(CONNECT_DATA=(SERVICE_NAME=orclsn)))")
            .setShortUrl("oracle:oci://orcl.host:55")
            .setSystem("oracle")
            .setSubtype("oci")
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
    return args(
        // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_tjvjcccn.html
        // https://www.ibm.com/support/knowledgecenter/en/SSEPGG_10.5.0/com.ibm.db2.luw.apdv.java.doc/src/tpc/imjcc_r0052342.html
        arg("jdbc:db2://db2.host")
            .setShortUrl("db2://db2.host:50000")
            .setSystem("db2")
            .setHost("db2.host")
            .setPort(50000)
            .build(),
        arg("jdbc:db2://db2.host")
            .setProperties(stdProps())
            .setShortUrl("db2://db2.host:9999")
            .setSystem("db2")
            .setUser("stdUserName")
            .setHost("db2.host")
            .setPort(9999)
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;")
            .setShortUrl("db2://db2.host:77")
            .setSystem("db2")
            .setUser("db2user")
            .setHost("db2.host")
            .setPort(77)
            .setName("db2db")
            .build(),
        arg("jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;")
            .setProperties(stdProps())
            .setShortUrl("db2://db2.host:77")
            .setSystem("db2")
            .setUser("db2user")
            .setHost("db2.host")
            .setPort(77)
            .setName("db2db")
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:as400://ashost:66/asdb:user=asuser;password=PW;")
            .setShortUrl("as400://ashost:66")
            .setSystem("db2")
            .setUser("asuser")
            .setHost("ashost")
            .setPort(66)
            .setName("asdb")
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
        arg("jdbc:sap://sap.host")
            .setShortUrl("sap://sap.host")
            .setSystem("hanadb")
            .setHost("sap.host")
            .build(),
        arg("jdbc:sap://sap.host")
            .setProperties(stdProps())
            .setShortUrl("sap://sap.host:9999")
            .setSystem("hanadb")
            .setUser("stdUserName")
            .setHost("sap.host")
            .setPort(9999)
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:sap://sap.host:88/?databaseName=sapdb&user=sapuser&password=PW")
            .setShortUrl("sap://sap.host:88")
            .setSystem("hanadb")
            .setUser("sapuser")
            .setHost("sap.host")
            .setPort(88)
            .setDb("sapdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("sapArguments")
  void testSapParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> informixArguments() {
    return args(
        // https://www.ibm.com/support/pages/how-configure-informix-jdbc-connection-string-connect-group
        arg("jdbc:informix-sqli://infxhost:99/infxdb:INFORMIXSERVER=infxsn;user=infxuser;password=PW")
            .setSystem("informix-sqli")
            .setUser("infxuser")
            .setShortUrl("informix-sqli://infxhost:99")
            .setHost("infxhost")
            .setPort(99)
            .setName("infxdb")
            .build(),
        arg("jdbc:informix-sqli://localhost:9088/stores_demo:INFORMIXSERVER=informix")
            .setSystem("informix-sqli")
            .setShortUrl("informix-sqli://localhost:9088")
            .setHost("localhost")
            .setPort(9088)
            .setName("stores_demo")
            .build(),
        arg("jdbc:informix-sqli://infxhost:99")
            .setSystem("informix-sqli")
            .setShortUrl("informix-sqli://infxhost:99")
            .setHost("infxhost")
            .setPort(99)
            .build(),
        arg("jdbc:informix-sqli://infxhost/")
            .setSystem("informix-sqli")
            .setShortUrl("informix-sqli://infxhost:9088")
            .setHost("infxhost")
            .setPort(9088)
            .build(),
        arg("jdbc:informix-sqli:")
            .setSystem("informix-sqli")
            .setShortUrl("informix-sqli:")
            .setPort(9088)
            .build(),

        // https://www.ibm.com/docs/en/informix-servers/12.10?topic=method-format-database-urls
        arg("jdbc:informix-direct://infxdb:999;user=infxuser;password=PW")
            .setSystem("informix-direct")
            .setShortUrl("informix-direct:")
            .setUser("infxuser")
            .setName("infxdb")
            .build(),
        arg("jdbc:informix-direct://infxdb;user=infxuser;password=PW")
            .setSystem("informix-direct")
            .setShortUrl("informix-direct:")
            .setUser("infxuser")
            .setName("infxdb")
            .build(),
        arg("jdbc:informix-direct://infxdb")
            .setSystem("informix-direct")
            .setShortUrl("informix-direct:")
            .setName("infxdb")
            .build(),
        arg("jdbc:informix-direct:")
            .setSystem("informix-direct")
            .setShortUrl("informix-direct:")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("informixArguments")
  void testInformixParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> h2Arguments() {
    return args(
        // http://www.h2database.com/html/features.html#database_url
        arg("jdbc:h2:mem:").setShortUrl("h2:mem:").setSystem("h2").setSubtype("mem").build(),
        arg("jdbc:h2:mem:")
            .setProperties(stdProps())
            .setShortUrl("h2:mem:")
            .setSystem("h2")
            .setSubtype("mem")
            .setUser("stdUserName")
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:h2:mem:h2db")
            .setShortUrl("h2:mem:")
            .setSystem("h2")
            .setSubtype("mem")
            .setName("h2db")
            .build(),
        arg("jdbc:h2:tcp://h2.host:111/path/h2db;user=h2user;password=PW")
            .setShortUrl("h2:tcp://h2.host:111")
            .setSystem("h2")
            .setSubtype("tcp")
            .setUser("h2user")
            .setHost("h2.host")
            .setPort(111)
            .setName("path/h2db")
            .build(),
        arg("jdbc:h2:ssl://h2.host:111/path/h2db;user=h2user;password=PW")
            .setShortUrl("h2:ssl://h2.host:111")
            .setSystem("h2")
            .setSubtype("ssl")
            .setUser("h2user")
            .setHost("h2.host")
            .setPort(111)
            .setName("path/h2db")
            .build(),
        arg("jdbc:h2:/data/h2file")
            .setShortUrl("h2:file:")
            .setSystem("h2")
            .setSubtype("file")
            .setName("/data/h2file")
            .build(),
        arg("jdbc:h2:file:~/h2file;USER=h2user;PASSWORD=PW")
            .setShortUrl("h2:file:")
            .setSystem("h2")
            .setSubtype("file")
            .setName("~/h2file")
            .build(),
        arg("jdbc:h2:file:/data/h2file")
            .setShortUrl("h2:file:")
            .setSystem("h2")
            .setSubtype("file")
            .setName("/data/h2file")
            .build(),
        arg("jdbc:h2:file:C:/data/h2file")
            .setShortUrl("h2:file:")
            .setSystem("h2")
            .setSubtype("file")
            .setName("c:/data/h2file")
            .build(),
        arg("jdbc:h2:zip:~/db.zip!/h2zip")
            .setShortUrl("h2:zip:")
            .setSystem("h2")
            .setSubtype("zip")
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
        arg("jdbc:hsqldb:hsdb")
            .setShortUrl("hsqldb:mem:")
            .setSystem("hsqldb")
            .setSubtype("mem")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:hsdb")
            .setProperties(stdProps())
            .setShortUrl("hsqldb:mem:")
            .setSystem("hsqldb")
            .setSubtype("mem")
            .setUser("stdUserName")
            .setName("hsdb")
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:hsqldb:mem:hsdb")
            .setShortUrl("hsqldb:mem:")
            .setSystem("hsqldb")
            .setSubtype("mem")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:mem:hsdb;shutdown=true")
            .setShortUrl("hsqldb:mem:")
            .setSystem("hsqldb")
            .setSubtype("mem")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:mem:hsdb?shutdown=true")
            .setShortUrl("hsqldb:mem:")
            .setSystem("hsqldb")
            .setSubtype("mem")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:file:hsdb")
            .setShortUrl("hsqldb:file:")
            .setSystem("hsqldb")
            .setSubtype("file")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:file:hsdb;user=aUserName;password=3xLVz")
            .setShortUrl("hsqldb:file:")
            .setSystem("hsqldb")
            .setSubtype("file")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:file:hsdb;create=false?user=aUserName&password=3xLVz")
            .setShortUrl("hsqldb:file:")
            .setSystem("hsqldb")
            .setSubtype("file")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:file:/loc/hsdb")
            .setShortUrl("hsqldb:file:")
            .setSystem("hsqldb")
            .setSubtype("file")
            .setUser("SA")
            .setName("/loc/hsdb")
            .build(),
        arg("jdbc:hsqldb:file:C:/hsdb")
            .setShortUrl("hsqldb:file:")
            .setSystem("hsqldb")
            .setSubtype("file")
            .setUser("SA")
            .setName("c:/hsdb")
            .build(),
        arg("jdbc:hsqldb:res:hsdb")
            .setShortUrl("hsqldb:res:")
            .setSystem("hsqldb")
            .setSubtype("res")
            .setUser("SA")
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:res:/cp/hsdb")
            .setShortUrl("hsqldb:res:")
            .setSystem("hsqldb")
            .setSubtype("res")
            .setUser("SA")
            .setName("/cp/hsdb")
            .build(),
        arg("jdbc:hsqldb:hsql://hs.host:333/hsdb")
            .setShortUrl("hsqldb:hsql://hs.host:333")
            .setSystem("hsqldb")
            .setSubtype("hsql")
            .setUser("SA")
            .setHost("hs.host")
            .setPort(333)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:hsqls://hs.host/hsdb")
            .setShortUrl("hsqldb:hsqls://hs.host:9001")
            .setSystem("hsqldb")
            .setSubtype("hsqls")
            .setUser("SA")
            .setHost("hs.host")
            .setPort(9001)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:http://hs.host")
            .setShortUrl("hsqldb:http://hs.host:80")
            .setSystem("hsqldb")
            .setSubtype("http")
            .setUser("SA")
            .setHost("hs.host")
            .setPort(80)
            .build(),
        arg("jdbc:hsqldb:http://hs.host:333/hsdb")
            .setShortUrl("hsqldb:http://hs.host:333")
            .setSystem("hsqldb")
            .setSubtype("http")
            .setUser("SA")
            .setHost("hs.host")
            .setPort(333)
            .setName("hsdb")
            .build(),
        arg("jdbc:hsqldb:https://127.0.0.1/hsdb")
            .setShortUrl("hsqldb:https://127.0.0.1:443")
            .setSystem("hsqldb")
            .setSubtype("https")
            .setUser("SA")
            .setHost("127.0.0.1")
            .setPort(443)
            .setName("hsdb")
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("hsqlDbArguments")
  void testHsqlDbParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static Stream<Arguments> derbyArguments() {
    return args(
        // https://db.apache.org/derby/papers/DerbyClientSpec.html#Connection+URL+Format
        // https://db.apache.org/derby/docs/10.8/devguide/cdevdvlp34964.html
        arg("jdbc:derby:derbydb")
            .setShortUrl("derby:directory:")
            .setSystem("derby")
            .setSubtype("directory")
            .setUser("APP")
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:derbydb")
            .setProperties(stdProps())
            .setShortUrl("derby:directory:")
            .setSystem("derby")
            .setSubtype("directory")
            .setUser("stdUserName")
            .setName("derbydb")
            .setDb("stdDatabaseName")
            .build(),
        arg("jdbc:derby:derbydb;user=derbyuser;password=pw")
            .setShortUrl("derby:directory:")
            .setSystem("derby")
            .setSubtype("directory")
            .setUser("derbyuser")
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:memory:derbydb")
            .setShortUrl("derby:memory:")
            .setSystem("derby")
            .setSubtype("memory")
            .setUser("APP")
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:memory:;databaseName=derbydb")
            .setShortUrl("derby:memory:")
            .setSystem("derby")
            .setSubtype("memory")
            .setUser("APP")
            .setDb("derbydb")
            .build(),
        arg("jdbc:derby:memory:derbydb;databaseName=altdb")
            .setShortUrl("derby:memory:")
            .setSystem("derby")
            .setSubtype("memory")
            .setUser("APP")
            .setName("derbydb")
            .setDb("altdb")
            .build(),
        arg("jdbc:derby:memory:derbydb;user=derbyuser;password=pw")
            .setShortUrl("derby:memory:")
            .setSystem("derby")
            .setSubtype("memory")
            .setUser("derbyuser")
            .setName("derbydb")
            .build(),
        arg("jdbc:derby://derby.host:222/memory:derbydb;create=true")
            .setShortUrl("derby:network://derby.host:222")
            .setSystem("derby")
            .setSubtype("network")
            .setUser("APP")
            .setHost("derby.host")
            .setPort(222)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby://derby.host/memory:derbydb;create=true;user=derbyuser;password=pw")
            .setShortUrl("derby:network://derby.host:1527")
            .setSystem("derby")
            .setSubtype("network")
            .setUser("derbyuser")
            .setHost("derby.host")
            .setPort(1527)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby://127.0.0.1:1527/memory:derbydb;create=true;user=derbyuser;password=pw")
            .setShortUrl("derby:network://127.0.0.1:1527")
            .setSystem("derby")
            .setSubtype("network")
            .setUser("derbyuser")
            .setHost("127.0.0.1")
            .setPort(1527)
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:directory:derbydb;user=derbyuser;password=pw")
            .setShortUrl("derby:directory:")
            .setSystem("derby")
            .setSubtype("directory")
            .setUser("derbyuser")
            .setName("derbydb")
            .build(),
        arg("jdbc:derby:classpath:/some/derbydb;user=derbyuser;password=pw")
            .setShortUrl("derby:classpath:")
            .setSystem("derby")
            .setSubtype("classpath")
            .setUser("derbyuser")
            .setName("/some/derbydb")
            .build(),
        arg("jdbc:derby:jar:/derbydb;user=derbyuser;password=pw")
            .setShortUrl("derby:jar:")
            .setSystem("derby")
            .setSubtype("jar")
            .setUser("derbyuser")
            .setName("/derbydb")
            .build(),
        arg("jdbc:derby:jar:(~/path/to/db.jar)/other/derbydb;user=derbyuser;password=pw")
            .setShortUrl("derby:jar:")
            .setSystem("derby")
            .setSubtype("jar")
            .setUser("derbyuser")
            .setName("(~/path/to/db.jar)/other/derbydb")
            .build(),
        arg("jdbc:derby:directory:/usr/ibm/pep/was9/ibm/websphere/appserver/profiles/my_profile/databases/ejbtimers/myhostname/ejbtimerdb")
            .setShortUrl("derby:directory:")
            .setSystem("derby")
            .setSubtype("directory")
            .setUser("APP")
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
            .setShortUrl("datadirect:sqlserver://server_name:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setHost("server_name")
            .setPort(1433)
            .setDb("dbname")
            .build(),
        arg("jdbc:datadirect:oracle://server_name:1521;ServiceName=your_servicename")
            .setShortUrl("datadirect:oracle://server_name:1521")
            .setSystem("oracle")
            .setSubtype("oracle")
            .setHost("server_name")
            .setPort(1521)
            .build(),
        arg("jdbc:datadirect:mysql://server_name:3306")
            .setShortUrl("datadirect:mysql://server_name:3306")
            .setSystem("mysql")
            .setSubtype("mysql")
            .setHost("server_name")
            .setPort(3306)
            .build(),
        arg("jdbc:datadirect:postgresql://server_name:5432;DatabaseName=dbname")
            .setShortUrl("datadirect:postgresql://server_name:5432")
            .setSystem("postgresql")
            .setSubtype("postgresql")
            .setHost("server_name")
            .setPort(5432)
            .setDb("dbname")
            .build(),
        arg("jdbc:datadirect:db2://server_name:50000;DatabaseName=dbname")
            .setShortUrl("datadirect:db2://server_name:50000")
            .setSystem("db2")
            .setSubtype("db2")
            .setHost("server_name")
            .setPort(50000)
            .setDb("dbname")
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
            .setShortUrl("tibcosoftware:sqlserver://server_name:1433")
            .setSystem("mssql")
            .setSubtype("sqlserver")
            .setHost("server_name")
            .setPort(1433)
            .setDb("dbname")
            .build(),
        arg("jdbc:tibcosoftware:oracle://server_name:1521;ServiceName=your_servicename")
            .setShortUrl("tibcosoftware:oracle://server_name:1521")
            .setSystem("oracle")
            .setSubtype("oracle")
            .setHost("server_name")
            .setPort(1521)
            .build(),
        arg("jdbc:tibcosoftware:mysql://server_name:3306")
            .setShortUrl("tibcosoftware:mysql://server_name:3306")
            .setSystem("mysql")
            .setSubtype("mysql")
            .setHost("server_name")
            .setPort(3306)
            .build(),
        arg("jdbc:tibcosoftware:postgresql://server_name:5432;DatabaseName=dbname")
            .setShortUrl("tibcosoftware:postgresql://server_name:5432")
            .setSystem("postgresql")
            .setSubtype("postgresql")
            .setHost("server_name")
            .setPort(5432)
            .setDb("dbname")
            .build(),
        arg("jdbc:tibcosoftware:db2://server_name:50000;DatabaseName=dbname")
            .setShortUrl("tibcosoftware:db2://server_name:50000")
            .setSystem("db2")
            .setSubtype("db2")
            .setHost("server_name")
            .setPort(50000)
            .setDb("dbname")
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
            .setShortUrl("mysql://example.com:50000")
            .setSystem("mysql")
            .setHost("example.com")
            .setPort(50000)
            .build(),
        arg("jdbc-secretsmanager:postgresql://example.com:50000/dbname")
            .setShortUrl("postgresql://example.com:50000")
            .setSystem("postgresql")
            .setHost("example.com")
            .setPort(50000)
            .setDb("dbname")
            .build(),
        arg("jdbc-secretsmanager:oracle:thin:@example.com:50000/ORCL")
            .setShortUrl("oracle:thin://example.com:50000")
            .setSystem("oracle")
            .setSubtype("thin")
            .setHost("example.com")
            .setPort(50000)
            .setName("orcl")
            .build(),
        arg("jdbc-secretsmanager:sqlserver://example.com:50000")
            .setShortUrl("sqlserver://example.com:50000")
            .setSystem("mssql")
            .setHost("example.com")
            .setPort(50000)
            .build());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("secretsManagerArguments")
  void testSecretsManagerParsing(ParseTestArgument argument) {
    testVerifySystemSubtypeParsingOfUrl(argument);
  }

  private static void testVerifySystemSubtypeParsingOfUrl(ParseTestArgument argument) {
    DbInfo info = parse(argument.url, argument.properties);
    DbInfo expected = argument.dbInfo;
    assertThat(info.getShortUrl()).isEqualTo(expected.getShortUrl());
    assertThat(info.getSystem()).isEqualTo(expected.getSystem());
    assertThat(info.getHost()).isEqualTo(expected.getHost());
    assertThat(info.getPort()).isEqualTo(expected.getPort());
    assertThat(info.getUser()).isEqualTo(expected.getUser());
    assertThat(info.getName()).isEqualTo(expected.getName());
    assertThat(info.getDb()).isEqualTo(expected.getDb());
    assertThat(info).isEqualTo(expected);
  }

  static class ParseTestArgument {
    final String url;
    final Properties properties;
    final DbInfo dbInfo;

    ParseTestArgument(ParseTestArgumentBuilder builder) {
      this.url = builder.url;
      this.properties = builder.properties;
      this.dbInfo =
          DbInfo.builder()
              .shortUrl(builder.shortUrl)
              .system(builder.system)
              .subtype(builder.subtype)
              .user(builder.user)
              .name(builder.name)
              .db(builder.db)
              .host(builder.host)
              .port(builder.port)
              .build();
    }

    @Override
    public String toString() {
      return dbInfo.getSystem() + ":" + dbInfo.getSubtype() + " parsing of " + url;
    }
  }

  static class ParseTestArgumentBuilder {
    String url;
    Properties properties;
    String shortUrl;
    String system;
    String subtype;
    String user;
    String host;
    Integer port;
    String name;
    String db;

    ParseTestArgumentBuilder(String url) {
      this.url = url;
    }

    ParseTestArgumentBuilder setProperties(Properties properties) {
      this.properties = properties;
      return this;
    }

    ParseTestArgumentBuilder setShortUrl(String shortUrl) {
      this.shortUrl = shortUrl;
      return this;
    }

    ParseTestArgumentBuilder setSystem(String system) {
      this.system = system;
      return this;
    }

    ParseTestArgumentBuilder setSubtype(String subtype) {
      this.subtype = subtype;
      return this;
    }

    ParseTestArgumentBuilder setUser(String user) {
      this.user = user;
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

    ParseTestArgumentBuilder setName(String name) {
      this.name = name;
      return this;
    }

    ParseTestArgumentBuilder setDb(String db) {
      this.db = db;
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
}
