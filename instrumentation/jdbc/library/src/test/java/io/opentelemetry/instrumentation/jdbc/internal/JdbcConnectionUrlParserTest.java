/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
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
    assertThat(JdbcConnectionUrlParser.parse(url, null)).isEqualTo(DbInfo.DEFAULT);
  }

  @Test
  void testNullUrlReturnsDefault() {
    assertThat(JdbcConnectionUrlParser.parse(null, null)).isEqualTo(DbInfo.DEFAULT);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @ArgumentsSource(ParsingProvider.class)
  void testVerifySystemSubtypeParsingOfUrl(ParseTestArgument argument) {
    DbInfo info = parse(argument.url, argument.properties);
    DbInfo expected = argument.dbInfo;
    assertThat(info.getSystem()).isEqualTo(expected.getSystem());
    assertThat(info.getHost()).isEqualTo(expected.getHost());
    assertThat(info.getPort()).isEqualTo(expected.getPort());
    assertThat(info.getUser()).isEqualTo(expected.getUser());
    assertThat(info.getName()).isEqualTo(expected.getName());
    assertThat(info.getDb()).isEqualTo(expected.getDb());
    assertThat(info).isEqualTo(expected);
  }

  static final class ParsingProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return args(
          // https://jdbc.postgresql.org/documentation/94/connect.html
          arg("jdbc:postgresql:///")
              .setSystem("postgresql")
              .setHost("localhost")
              .setPort(5432)
              .build(),
          arg("jdbc:postgresql:///")
              .setProperties(stdProps())
              .setSystem("postgresql")
              .setUser("stdUserName")
              .setHost("stdServerName")
              .setPort(9999)
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:postgresql://pg.host")
              .setSystem("postgresql")
              .setHost("pg.host")
              .setPort(5432)
              .build(),
          arg("jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW")
              .setSystem("postgresql")
              .setUser("pguser")
              .setHost("pg.host")
              .setPort(11)
              .setDb("pgdb")
              .build(),
          arg("jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW")
              .setProperties(stdProps())
              .setSystem("postgresql")
              .setUser("pguser")
              .setHost("pg.host")
              .setPort(11)
              .setDb("pgdb")
              .build(),

          // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
          // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
          arg("jdbc:mysql:///").setSystem("mysql").setHost("localhost").setPort(3306).build(),
          arg("jdbc:mysql:///")
              .setProperties(stdProps())
              .setSystem("mysql")
              .setUser("stdUserName")
              .setHost("stdServerName")
              .setPort(9999)
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:mysql://my.host").setSystem("mysql").setHost("my.host").setPort(3306).build(),
          arg("jdbc:mysql://my.host?user=myuser&password=PW")
              .setSystem("mysql")
              .setUser("myuser")
              .setHost("my.host")
              .setPort(3306)
              .build(),
          arg("jdbc:mysql://my.host:22/mydb?user=myuser&password=PW")
              .setSystem("mysql")
              .setUser("myuser")
              .setHost("my.host")
              .setPort(22)
              .setDb("mydb")
              .build(),
          arg("jdbc:mysql://127.0.0.1:22/mydb?user=myuser&password=PW")
              .setProperties(stdProps())
              .setSystem("mysql")
              .setUser("myuser")
              .setHost("127.0.0.1")
              .setPort(22)
              .setDb("mydb")
              .build(),
          arg("jdbc:mysql://myuser:password@my.host:22/mydb")
              .setSystem("mysql")
              .setUser("myuser")
              .setHost("my.host")
              .setPort(22)
              .setDb("mydb")
              .build(),

          // https://mariadb.com/kb/en/library/about-mariadb-connector-j/#connection-strings
          arg("jdbc:mariadb:127.0.0.1:33/mdbdb")
              .setSystem("mariadb")
              .setHost("127.0.0.1")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:localhost/mdbdb")
              .setSystem("mariadb")
              .setHost("localhost")
              .setPort(3306)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:localhost/mdbdb?user=mdbuser&password=PW")
              .setProperties(stdProps())
              .setSystem("mariadb")
              .setUser("mdbuser")
              .setHost("localhost")
              .setPort(9999)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:localhost:33/mdbdb")
              .setProperties(stdProps())
              .setSystem("mariadb")
              .setUser("stdUserName")
              .setHost("localhost")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb://mdb.host:33/mdbdb?user=mdbuser&password=PW")
              .setSystem("mariadb")
              .setUser("mdbuser")
              .setHost("mdb.host")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:aurora://mdb.host/mdbdb")
              .setSystem("mariadb")
              .setSubtype("aurora")
              .setHost("mdb.host")
              .setPort(3306)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mysql:aurora://mdb.host/mdbdb")
              .setSystem("mysql")
              .setSubtype("aurora")
              .setHost("mdb.host")
              .setPort(3306)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mysql:failover://localhost/mdbdb?autoReconnect=true")
              .setSystem("mysql")
              .setSubtype("failover")
              .setHost("localhost")
              .setPort(3306)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:failover://mdb.host1:33,mdb.host/mdbdb?characterEncoding=utf8")
              .setSystem("mariadb")
              .setSubtype("failover")
              .setHost("mdb.host1")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:sequential://mdb.host1,mdb.host2:33/mdbdb")
              .setSystem("mariadb")
              .setSubtype("sequential")
              .setHost("mdb.host1")
              .setPort(3306)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:loadbalance://127.0.0.1:33,mdb.host/mdbdb")
              .setSystem("mariadb")
              .setSubtype("loadbalance")
              .setHost("127.0.0.1")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:loadbalance://127.0.0.1:33/mdbdb")
              .setSystem("mariadb")
              .setSubtype("loadbalance")
              .setHost("127.0.0.1")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:loadbalance://[2001:0660:7401:0200:0000:0000:0edf:bdd7]:33,mdb.host/mdbdb")
              .setSystem("mariadb")
              .setSubtype("loadbalance")
              .setHost("2001:0660:7401:0200:0000:0000:0edf:bdd7")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mysql:loadbalance://127.0.0.1,127.0.0.1:3306/mdbdb?user=mdbuser&password=PW")
              .setSystem("mysql")
              .setSubtype("loadbalance")
              .setUser("mdbuser")
              .setHost("127.0.0.1")
              .setPort(3306)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mariadb:replication://localhost:33,anotherhost:3306/mdbdb")
              .setSystem("mariadb")
              .setSubtype("replication")
              .setHost("localhost")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mysql:replication://address=(HOST=127.0.0.1)(port=33)(user=mdbuser)(password=PW),address=(host=mdb.host)(port=3306)(user=otheruser)(password=PW)/mdbdb?user=wrong&password=PW")
              .setSystem("mysql")
              .setSubtype("replication")
              .setUser("mdbuser")
              .setHost("127.0.0.1")
              .setPort(33)
              .setDb("mdbdb")
              .build(),
          arg("jdbc:mysql:replication://address=(HOST=mdb.host),address=(host=anotherhost)(port=3306)(user=wrong)(password=PW)/mdbdb?user=mdbuser&password=PW")
              .setSystem("mysql")
              .setSubtype("replication")
              .setUser("mdbuser")
              .setHost("mdb.host")
              .setPort(3306)
              .setDb("mdbdb")
              .build(),

          // https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
          arg("jdbc:microsoft:sqlserver://;")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setHost("localhost")
              .setPort(1433)
              .build(),
          arg("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9")
              .setSystem("mssql")
              .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
              .setPort(1433)
              .build(),
          arg("jdbc:sqlserver://;serverName=2001:0db8:85a3:0000:0000:8a2e:0370:7334")
              .setSystem("mssql")
              .setHost("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]")
              .setPort(1433)
              .build(),
          arg("jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43")
              .setSystem("mssql")
              .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
              .setPort(43)
              .build(),
          arg("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance")
              .setSystem("mssql")
              .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
              .setPort(1433)
              .setName("ssinstance")
              .build(),
          arg("jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance]:43")
              .setSystem("mssql")
              .setHost("[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]")
              .setPort(43)
              .setName("ssinstance")
              .build(),
          arg("jdbc:microsoft:sqlserver://;")
              .setProperties(stdProps())
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setUser("stdUserName")
              .setHost("stdServerName")
              .setPort(9999)
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:sqlserver://ss.host\\ssinstance:44;databaseName=ssdb;user=ssuser;password=pw")
              .setSystem("mssql")
              .setUser("ssuser")
              .setHost("ss.host")
              .setPort(44)
              .setName("ssinstance")
              .setDb("ssdb")
              .build(),
          arg("jdbc:sqlserver://;serverName=ss.host\\ssinstance:44;DatabaseName=;")
              .setSystem("mssql")
              .setHost("ss.host")
              .setPort(44)
              .setName("ssinstance")
              .build(),
          arg("jdbc:sqlserver://ss.host;serverName=althost;DatabaseName=ssdb;")
              .setSystem("mssql")
              .setHost("ss.host")
              .setPort(1433)
              .setDb("ssdb")
              .build(),
          arg("jdbc:microsoft:sqlserver://ss.host:44;DatabaseName=ssdb;user=ssuser;password=pw;user=ssuser2;")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setUser("ssuser")
              .setHost("ss.host")
              .setPort(44)
              .setDb("ssdb")
              .build(),

          // http://jtds.sourceforge.net/faq.html#urlFormat
          arg("jdbc:jtds:sqlserver://ss.host/ssdb")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setHost("ss.host")
              .setPort(1433)
              .setDb("ssdb")
              .build(),
          arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setHost("ss.host")
              .setPort(1433)
              .setDb("ssdb")
              .build(),
          arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb;user=ssuser")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setUser("ssuser")
              .setHost("ss.host")
              .setPort(1433)
              .setDb("ssdb")
              .build(),
          arg("jdbc:jtds:sqlserver://ss.host/ssdb;instance=ssinstance")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setHost("ss.host")
              .setPort(1433)
              .setName("ssinstance")
              .setDb("ssdb")
              .build(),
          arg("jdbc:jtds:sqlserver://ss.host:1444/ssdb;instance=ssinstance")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setHost("ss.host")
              .setPort(1444)
              .setName("ssinstance")
              .setDb("ssdb")
              .build(),
          arg("jdbc:jtds:sqlserver://ss.host:1433/ssdb;instance=ssinstance;user=ssuser")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setUser("ssuser")
              .setHost("ss.host")
              .setPort(1433)
              .setName("ssinstance")
              .setDb("ssdb")
              .build(),

          // https://docs.oracle.com/cd/B28359_01/java.111/b31224/urls.htm
          // https://docs.oracle.com/cd/B28359_01/java.111/b31224/jdbcthin.htm
          arg("jdbc:oracle:thin:orcluser/PW@localhost:55:orclsn")
              .setSystem("oracle")
              .setSubtype("thin")
              .setUser("orcluser")
              .setHost("localhost")
              .setPort(55)
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:thin:orcluser/PW@//orcl.host:55/orclsn")
              .setSystem("oracle")
              .setSubtype("thin")
              .setUser("orcluser")
              .setHost("orcl.host")
              .setPort(55)
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:thin:orcluser/PW@127.0.0.1:orclsn")
              .setSystem("oracle")
              .setSubtype("thin")
              .setUser("orcluser")
              .setHost("127.0.0.1")
              .setPort(1521) // Default Oracle port assumed as not specified in the URL
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:thin:orcluser/PW@//orcl.host/orclsn")
              .setSystem("oracle")
              .setSubtype("thin")
              .setUser("orcluser")
              .setHost("orcl.host")
              .setPort(1521) // Default Oracle port assumed as not specified in the URL
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:thin:@//orcl.host:55/orclsn")
              .setSystem("oracle")
              .setSubtype("thin")
              .setHost("orcl.host")
              .setPort(55)
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:thin:@ldap://orcl.host:55/some,cn=OracleContext,dc=com")
              .setSystem("oracle")
              .setSubtype("thin")
              .setHost("orcl.host")
              .setPort(55)
              .setName("some,cn=oraclecontext,dc=com")
              .build(),
          arg("jdbc:oracle:thin:127.0.0.1:orclsn")
              .setSystem("oracle")
              .setSubtype("thin")
              .setHost("127.0.0.1")
              .setPort(1521) // Default Oracle port assumed as not specified in the URL
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:thin:orcl.host:orclsn")
              .setProperties(stdProps())
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
              .setSystem("oracle")
              .setSubtype("thin")
              .setHost("127.0.0.1")
              .setPort(666)
              .setName("orclsn")
              .build(),

          // https://docs.oracle.com/cd/B28359_01/java.111/b31224/instclnt.htm
          arg("jdbc:oracle:drivertype:orcluser/PW@orcl.host:55/orclsn")
              .setSystem("oracle")
              .setSubtype("drivertype")
              .setUser("orcluser")
              .setHost("orcl.host")
              .setPort(55)
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:oci8:@").setSystem("oracle").setSubtype("oci8").setPort(1521).build(),
          arg("jdbc:oracle:oci8:@")
              .setProperties(stdProps())
              .setSystem("oracle")
              .setSubtype("oci8")
              .setUser("stdUserName")
              .setHost("stdServerName")
              .setPort(9999)
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:oracle:oci8:@orclsn")
              .setSystem("oracle")
              .setSubtype("oci8")
              .setPort(1521)
              .setName("orclsn")
              .build(),
          arg("jdbc:oracle:oci:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=orcl.host)(PORT=55))(CONNECT_DATA=(SERVICE_NAME=orclsn)))")
              .setSystem("oracle")
              .setSubtype("oci")
              .setHost("orcl.host")
              .setPort(55)
              .setName("orclsn")
              .build(),

          // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_tjvjcccn.html
          // https://www.ibm.com/support/knowledgecenter/en/SSEPGG_10.5.0/com.ibm.db2.luw.apdv.java.doc/src/tpc/imjcc_r0052342.html
          arg("jdbc:db2://db2.host").setSystem("db2").setHost("db2.host").setPort(50000).build(),
          arg("jdbc:db2://db2.host")
              .setProperties(stdProps())
              .setSystem("db2")
              .setUser("stdUserName")
              .setHost("db2.host")
              .setPort(9999)
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;")
              .setSystem("db2")
              .setUser("db2user")
              .setHost("db2.host")
              .setPort(77)
              .setName("db2db")
              .build(),
          arg("jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;")
              .setProperties(stdProps())
              .setSystem("db2")
              .setUser("db2user")
              .setHost("db2.host")
              .setPort(77)
              .setName("db2db")
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:as400://ashost:66/asdb:user=asuser;password=PW;")
              .setSystem("db2")
              .setUser("asuser")
              .setHost("ashost")
              .setPort(66)
              .setName("asdb")
              .build(),

          // http://www.h2database.com/html/features.html#database_url
          arg("jdbc:h2:mem:").setSystem("h2").setSubtype("mem").build(),
          arg("jdbc:h2:mem:")
              .setProperties(stdProps())
              .setSystem("h2")
              .setSubtype("mem")
              .setUser("stdUserName")
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:h2:mem:h2db").setSystem("h2").setSubtype("mem").setName("h2db").build(),
          arg("jdbc:h2:tcp://h2.host:111/path/h2db;user=h2user;password=PW")
              .setSystem("h2")
              .setSubtype("tcp")
              .setUser("h2user")
              .setHost("h2.host")
              .setPort(111)
              .setName("path/h2db")
              .build(),
          arg("jdbc:h2:ssl://h2.host:111/path/h2db;user=h2user;password=PW")
              .setSystem("h2")
              .setSubtype("ssl")
              .setUser("h2user")
              .setHost("h2.host")
              .setPort(111)
              .setName("path/h2db")
              .build(),
          arg("jdbc:h2:/data/h2file")
              .setSystem("h2")
              .setSubtype("file")
              .setName("/data/h2file")
              .build(),
          arg("jdbc:h2:file:~/h2file;USER=h2user;PASSWORD=PW")
              .setSystem("h2")
              .setSubtype("file")
              .setName("~/h2file")
              .build(),
          arg("jdbc:h2:file:/data/h2file")
              .setSystem("h2")
              .setSubtype("file")
              .setName("/data/h2file")
              .build(),
          arg("jdbc:h2:file:C:/data/h2file")
              .setSystem("h2")
              .setSubtype("file")
              .setName("c:/data/h2file")
              .build(),
          arg("jdbc:h2:zip:~/db.zip!/h2zip")
              .setSystem("h2")
              .setSubtype("zip")
              .setName("~/db.zip!/h2zip")
              .build(),

          // http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html
          arg("jdbc:hsqldb:hsdb")
              .setSystem("hsqldb")
              .setSubtype("mem")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:hsdb")
              .setProperties(stdProps())
              .setSystem("hsqldb")
              .setSubtype("mem")
              .setUser("stdUserName")
              .setName("hsdb")
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:hsqldb:mem:hsdb")
              .setSystem("hsqldb")
              .setSubtype("mem")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:mem:hsdb;shutdown=true")
              .setSystem("hsqldb")
              .setSubtype("mem")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:mem:hsdb?shutdown=true")
              .setSystem("hsqldb")
              .setSubtype("mem")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:file:hsdb")
              .setSystem("hsqldb")
              .setSubtype("file")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:file:hsdb;user=aUserName;password=3xLVz")
              .setSystem("hsqldb")
              .setSubtype("file")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:file:hsdb;create=false?user=aUserName&password=3xLVz")
              .setSystem("hsqldb")
              .setSubtype("file")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:file:/loc/hsdb")
              .setSystem("hsqldb")
              .setSubtype("file")
              .setUser("SA")
              .setName("/loc/hsdb")
              .build(),
          arg("jdbc:hsqldb:file:C:/hsdb")
              .setSystem("hsqldb")
              .setSubtype("file")
              .setUser("SA")
              .setName("c:/hsdb")
              .build(),
          arg("jdbc:hsqldb:res:hsdb")
              .setSystem("hsqldb")
              .setSubtype("res")
              .setUser("SA")
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:res:/cp/hsdb")
              .setSystem("hsqldb")
              .setSubtype("res")
              .setUser("SA")
              .setName("/cp/hsdb")
              .build(),
          arg("jdbc:hsqldb:hsql://hs.host:333/hsdb")
              .setSystem("hsqldb")
              .setSubtype("hsql")
              .setUser("SA")
              .setHost("hs.host")
              .setPort(333)
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:hsqls://hs.host/hsdb")
              .setSystem("hsqldb")
              .setSubtype("hsqls")
              .setUser("SA")
              .setHost("hs.host")
              .setPort(9001)
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:http://hs.host")
              .setSystem("hsqldb")
              .setSubtype("http")
              .setUser("SA")
              .setHost("hs.host")
              .setPort(80)
              .build(),
          arg("jdbc:hsqldb:http://hs.host:333/hsdb")
              .setSystem("hsqldb")
              .setSubtype("http")
              .setUser("SA")
              .setHost("hs.host")
              .setPort(333)
              .setName("hsdb")
              .build(),
          arg("jdbc:hsqldb:https://127.0.0.1/hsdb")
              .setSystem("hsqldb")
              .setSubtype("https")
              .setUser("SA")
              .setHost("127.0.0.1")
              .setPort(443)
              .setName("hsdb")
              .build(),

          // https://db.apache.org/derby/papers/DerbyClientSpec.html#Connection+URL+Format
          // https://db.apache.org/derby/docs/10.8/devguide/cdevdvlp34964.html
          arg("jdbc:derby:derbydb")
              .setSystem("derby")
              .setSubtype("directory")
              .setUser("APP")
              .setName("derbydb")
              .build(),
          arg("jdbc:derby:derbydb")
              .setProperties(stdProps())
              .setSystem("derby")
              .setSubtype("directory")
              .setUser("stdUserName")
              .setName("derbydb")
              .setDb("stdDatabaseName")
              .build(),
          arg("jdbc:derby:derbydb;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("directory")
              .setUser("derbyuser")
              .setName("derbydb")
              .build(),
          arg("jdbc:derby:memory:derbydb")
              .setSystem("derby")
              .setSubtype("memory")
              .setUser("APP")
              .setName("derbydb")
              .build(),
          arg("jdbc:derby:memory:;databaseName=derbydb")
              .setSystem("derby")
              .setSubtype("memory")
              .setUser("APP")
              .setDb("derbydb")
              .build(),
          arg("jdbc:derby:memory:derbydb;databaseName=altdb")
              .setSystem("derby")
              .setSubtype("memory")
              .setUser("APP")
              .setName("derbydb")
              .setDb("altdb")
              .build(),
          arg("jdbc:derby:memory:derbydb;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("memory")
              .setUser("derbyuser")
              .setName("derbydb")
              .build(),
          arg("jdbc:derby://derby.host:222/memory:derbydb;create=true")
              .setSystem("derby")
              .setSubtype("network")
              .setUser("APP")
              .setHost("derby.host")
              .setPort(222)
              .setName("derbydb")
              .build(),
          arg("jdbc:derby://derby.host/memory:derbydb;create=true;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("network")
              .setUser("derbyuser")
              .setHost("derby.host")
              .setPort(1527)
              .setName("derbydb")
              .build(),
          arg("jdbc:derby://127.0.0.1:1527/memory:derbydb;create=true;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("network")
              .setUser("derbyuser")
              .setHost("127.0.0.1")
              .setPort(1527)
              .setName("derbydb")
              .build(),
          arg("jdbc:derby:directory:derbydb;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("directory")
              .setUser("derbyuser")
              .setName("derbydb")
              .build(),
          arg("jdbc:derby:classpath:/some/derbydb;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("classpath")
              .setUser("derbyuser")
              .setName("/some/derbydb")
              .build(),
          arg("jdbc:derby:jar:/derbydb;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("jar")
              .setUser("derbyuser")
              .setName("/derbydb")
              .build(),
          arg("jdbc:derby:jar:(~/path/to/db.jar)/other/derbydb;user=derbyuser;password=pw")
              .setSystem("derby")
              .setSubtype("jar")
              .setUser("derbyuser")
              .setName("(~/path/to/db.jar)/other/derbydb")
              .build(),

          // https://docs.progress.com/bundle/datadirect-connect-jdbc-51/page/URL-Formats-DataDirect-Connect-for-JDBC-Drivers.html
          arg("jdbc:datadirect:sqlserver://server_name:1433;DatabaseName=dbname")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setHost("server_name")
              .setPort(1433)
              .setDb("dbname")
              .build(),
          arg("jdbc:datadirect:oracle://server_name:1521;ServiceName=your_servicename")
              .setSystem("oracle")
              .setSubtype("oracle")
              .setHost("server_name")
              .setPort(1521)
              .build(),
          arg("jdbc:datadirect:mysql://server_name:3306")
              .setSystem("mysql")
              .setSubtype("mysql")
              .setHost("server_name")
              .setPort(3306)
              .build(),
          arg("jdbc:datadirect:postgresql://server_name:5432;DatabaseName=dbname")
              .setSystem("postgresql")
              .setSubtype("postgresql")
              .setHost("server_name")
              .setPort(5432)
              .setDb("dbname")
              .build(),
          arg("jdbc:datadirect:db2://server_name:50000;DatabaseName=dbname")
              .setSystem("db2")
              .setSubtype("db2")
              .setHost("server_name")
              .setPort(50000)
              .setDb("dbname")
              .build(),

          // "the TIBCO JDBC drivers are based on the Progress DataDirect Connect drivers"
          // https://community.jaspersoft.com/documentation/tibco-jasperreports-server-administrator-guide/v601/working-data-sources
          arg("jdbc:tibcosoftware:sqlserver://server_name:1433;DatabaseName=dbname")
              .setSystem("mssql")
              .setSubtype("sqlserver")
              .setHost("server_name")
              .setPort(1433)
              .setDb("dbname")
              .build(),
          arg("jdbc:tibcosoftware:oracle://server_name:1521;ServiceName=your_servicename")
              .setSystem("oracle")
              .setSubtype("oracle")
              .setHost("server_name")
              .setPort(1521)
              .build(),
          arg("jdbc:tibcosoftware:mysql://server_name:3306")
              .setSystem("mysql")
              .setSubtype("mysql")
              .setHost("server_name")
              .setPort(3306)
              .build(),
          arg("jdbc:tibcosoftware:postgresql://server_name:5432;DatabaseName=dbname")
              .setSystem("postgresql")
              .setSubtype("postgresql")
              .setHost("server_name")
              .setPort(5432)
              .setDb("dbname")
              .build(),
          arg("jdbc:tibcosoftware:db2://server_name:50000;DatabaseName=dbname")
              .setSystem("db2")
              .setSubtype("db2")
              .setHost("server_name")
              .setPort(50000)
              .setDb("dbname")
              .build(),

          // https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_jdbc.html
          arg("jdbc-secretsmanager:mysql://example.com:50000")
              .setSystem("mysql")
              .setHost("example.com")
              .setPort(50000)
              .build(),
          arg("jdbc-secretsmanager:postgresql://example.com:50000/dbname")
              .setSystem("postgresql")
              .setHost("example.com")
              .setPort(50000)
              .setDb("dbname")
              .build(),
          arg("jdbc-secretsmanager:oracle:thin:@example.com:50000/ORCL")
              .setSystem("oracle")
              .setSubtype("thin")
              .setHost("example.com")
              .setPort(50000)
              .setName("orcl")
              .build(),
          arg("jdbc-secretsmanager:sqlserver://example.com:50000")
              .setSystem("mssql")
              .setHost("example.com")
              .setPort(50000)
              .build());
    }
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
