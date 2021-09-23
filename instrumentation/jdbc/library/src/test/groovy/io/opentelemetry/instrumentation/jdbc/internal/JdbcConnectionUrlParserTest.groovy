/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal

import spock.lang.Shared
import spock.lang.Specification

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser.parse

class JdbcConnectionUrlParserTest extends Specification {

  @Shared
  def stdProps = {
    def prop = new Properties()
    // https://download.oracle.com/otn-pub/jcp/jdbc-4_1-mrel-spec/jdbc4.1-fr-spec.pdf
    prop.setProperty("databaseName", "stdDatabaseName")
    prop.setProperty("dataSourceName", "stdDatasourceName")
    prop.setProperty("description", "Some description")
    prop.setProperty("networkProtocol", "stdProto")
    prop.setProperty("password", "PASSWORD!")
    prop.setProperty("portNumber", "9999")
    prop.setProperty("roleName", "stdRoleName")
    prop.setProperty("serverName", "stdServerName")
    prop.setProperty("user", "stdUserName")
    return prop
  }()

  def "invalid url returns default"() {
    expect:
    parse(url, null) == DbInfo.DEFAULT

    where:
    url            | _
    null           | _
    ""             | _
    "jdbc:"        | _
    "jdbc::"       | _
    "bogus:string" | _
  }

  def "verify #system:#subtype parsing of #url"() {
    setup:
    def info = parse(url, props)

    expect:
    info.shortUrl == expected.shortUrl
    info.system == expected.system
    info.host == expected.host
    info.port == expected.port
    info.user == expected.user
    info.name == expected.name
    info.db == expected.db

    info == expected

    where:
    url                                                                                               | props    | shortUrl                                                           | system       | subtype       | user          | host                                        | port  | name                               | db
    // https://jdbc.postgresql.org/documentation/94/connect.html                                                 
    "jdbc:postgresql:///"                                                                             | null     | "postgresql://localhost:5432"                                      | "postgresql" | null          | null          | "localhost"                                 | 5432  | null                               | null
    "jdbc:postgresql:///"                                                                             | stdProps | "postgresql://stdServerName:9999"                                  | "postgresql" | null          | "stdUserName" | "stdServerName"                             | 9999  | null                               | "stdDatabaseName"
    "jdbc:postgresql://pg.host"                                                                       | null     | "postgresql://pg.host:5432"                                        | "postgresql" | null          | null          | "pg.host"                                   | 5432  | null                               | null
    "jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW"                                       | null     | "postgresql://pg.host:11"                                          | "postgresql" | null          | "pguser"      | "pg.host"                                   | 11    | null                               | "pgdb"

    "jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW"                                       | stdProps | "postgresql://pg.host:11"                                          | "postgresql" | null          | "pguser"      | "pg.host"                                   | 11    | null                               | "pgdb"

    // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
    // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
    "jdbc:mysql:///"                                                                                  | null     | "mysql://localhost:3306"                                           | "mysql"      | null          | null          | "localhost"                                 | 3306  | null                               | null
    "jdbc:mysql:///"                                                                                  | stdProps | "mysql://stdServerName:9999"                                       | "mysql"      | null          | "stdUserName" | "stdServerName"                             | 9999  | null                               | "stdDatabaseName"
    "jdbc:mysql://my.host"                                                                            | null     | "mysql://my.host:3306"                                             | "mysql"      | null          | null          | "my.host"                                   | 3306  | null                               | null
    "jdbc:mysql://my.host?user=myuser&password=PW"                                                    | null     | "mysql://my.host:3306"                                             | "mysql"      | null          | "myuser"      | "my.host"                                   | 3306  | null                               | null
    "jdbc:mysql://my.host:22/mydb?user=myuser&password=PW"                                            | null     | "mysql://my.host:22"                                               | "mysql"      | null          | "myuser"      | "my.host"                                   | 22    | null                               | "mydb"
    "jdbc:mysql://127.0.0.1:22/mydb?user=myuser&password=PW"                                          | stdProps | "mysql://127.0.0.1:22"                                             | "mysql"      | null          | "myuser"      | "127.0.0.1"                                 | 22    | null                               | "mydb"

    // https://mariadb.com/kb/en/library/about-mariadb-connector-j/#connection-strings                                                 
    "jdbc:mariadb:127.0.0.1:33/mdbdb"                                                                 | null     | "mariadb://127.0.0.1:33"                                           | "mariadb"    | null          | null          | "127.0.0.1"                                 | 33    | null                               | "mdbdb"
    "jdbc:mariadb:localhost/mdbdb"                                                                    | null     | "mariadb://localhost:3306"                                         | "mariadb"    | null          | null          | "localhost"                                 | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:localhost/mdbdb?user=mdbuser&password=PW"                                           | stdProps | "mariadb://localhost:9999"                                         | "mariadb"    | null          | "mdbuser"     | "localhost"                                 | 9999  | null                               | "mdbdb"
    "jdbc:mariadb:localhost:33/mdbdb"                                                                 | stdProps | "mariadb://localhost:33"                                           | "mariadb"    | null          | "stdUserName" | "localhost"                                 | 33    | null                               | "mdbdb"
    "jdbc:mariadb://mdb.host:33/mdbdb?user=mdbuser&password=PW"                                       | null     | "mariadb://mdb.host:33"                                            | "mariadb"    | null          | "mdbuser"     | "mdb.host"                                  | 33    | null                               | "mdbdb"
    "jdbc:mariadb:aurora://mdb.host/mdbdb"                                                            | null     | "mariadb:aurora://mdb.host:3306"                                   | "mariadb"    | "aurora"      | null          | "mdb.host"                                  | 3306  | null                               | "mdbdb"
    "jdbc:mysql:aurora://mdb.host/mdbdb"                                                              | null     | "mysql:aurora://mdb.host:3306"                                     | "mysql"      | "aurora"      | null          | "mdb.host"                                  | 3306  | null                               | "mdbdb"
    "jdbc:mysql:failover://localhost/mdbdb?autoReconnect=true"                                        | null     | "mysql:failover://localhost:3306"                                  | "mysql"      | "failover"    | null          | "localhost"                                 | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:failover://mdb.host1:33,mdb.host/mdbdb?characterEncoding=utf8"                      | null     | "mariadb:failover://mdb.host1:33"                                  | "mariadb"    | "failover"    | null          | "mdb.host1"                                 | 33    | null                               | "mdbdb"
    "jdbc:mariadb:sequential://mdb.host1,mdb.host2:33/mdbdb"                                          | null     | "mariadb:sequential://mdb.host1:3306"                              | "mariadb"    | "sequential"  | null          | "mdb.host1"                                 | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:loadbalance://127.0.0.1:33,mdb.host/mdbdb"                                          | null     | "mariadb:loadbalance://127.0.0.1:33"                               | "mariadb"    | "loadbalance" | null          | "127.0.0.1"                                 | 33    | null                               | "mdbdb"
    "jdbc:mariadb:loadbalance://[2001:0660:7401:0200:0000:0000:0edf:bdd7]:33,mdb.host/mdbdb"          | null     | "mariadb:loadbalance://2001:0660:7401:0200:0000:0000:0edf:bdd7:33" | "mariadb"    | "loadbalance" | null          | "2001:0660:7401:0200:0000:0000:0edf:bdd7"   | 33    | null                               | "mdbdb"
    "jdbc:mysql:loadbalance://127.0.0.1,127.0.0.1:3306/mdbdb?user=mdbuser&password=PW"                | null     | "mysql:loadbalance://127.0.0.1:3306"                               | "mysql"      | "loadbalance" | "mdbuser"     | "127.0.0.1"                                 | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:replication://localhost:33,anotherhost:3306/mdbdb"                                  | null     | "mariadb:replication://localhost:33"                               | "mariadb"    | "replication" | null          | "localhost"                                 | 33    | null                               | "mdbdb"
    "jdbc:mysql:replication://address=(HOST=127.0.0.1)(port=33)(user=mdbuser)(password=PW)," +
      "address=(host=mdb.host)(port=3306)(user=otheruser)(password=PW)/mdbdb?user=wrong&password=PW"  | null     | "mysql:replication://127.0.0.1:33"                                 | "mysql"      | "replication" | "mdbuser"     | "127.0.0.1"                                 | 33    | null                               | "mdbdb"
    "jdbc:mysql:replication://address=(HOST=mdb.host)," +
      "address=(host=anotherhost)(port=3306)(user=wrong)(password=PW)/mdbdb?user=mdbuser&password=PW" | null     | "mysql:replication://mdb.host:3306"                                | "mysql"      | "replication" | "mdbuser"     | "mdb.host"                                  | 3306  | null                               | "mdbdb"

    //https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
    "jdbc:microsoft:sqlserver://;"                                                                    | null     | "microsoft:sqlserver://localhost:1433"                             | "mssql"      | "sqlserver"   | null          | "localhost"                                 | 1433  | null                               | null
    "jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9"                             | null     | "sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:1433"        | "mssql"      | null          | null          | "[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]"  | 1433  | null                               | null
    "jdbc:sqlserver://;serverName=2001:0db8:85a3:0000:0000:8a2e:0370:7334"                            | null     | "sqlserver://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:1433"       | "mssql"      | null          | null          | "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]" | 1433  | null                               | null
    "jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43"                        | null     | "sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43"          | "mssql"      | null          | null          | "[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]"  | 43    | null                               | null
    "jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance"                 | null     | "sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:1433"        | "mssql"      | null          | null          | "[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]"  | 1433  | "ssinstance"                       | null
    "jdbc:sqlserver://;serverName=[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\ssinstance]:43"            | null     | "sqlserver://[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]:43"          | "mssql"      | null          | null          | "[3ffe:8311:eeee:f70f:0:5eae:10.203.31.9]"  | 43    | "ssinstance"                       | null
    "jdbc:microsoft:sqlserver://;"                                                                    | stdProps | "microsoft:sqlserver://stdServerName:9999"                         | "mssql"      | "sqlserver"   | "stdUserName" | "stdServerName"                             | 9999  | null                               | "stdDatabaseName"
    "jdbc:sqlserver://ss.host\\ssinstance:44;databaseName=ssdb;user=ssuser;password=pw"               | null     | "sqlserver://ss.host:44"                                           | "mssql"      | null          | "ssuser"      | "ss.host"                                   | 44    | "ssinstance"                       | "ssdb"
    "jdbc:sqlserver://;serverName=ss.host\\ssinstance:44;DatabaseName=;"                              | null     | "sqlserver://ss.host:44"                                           | "mssql"      | null          | null          | "ss.host"                                   | 44    | "ssinstance"                       | null
    "jdbc:sqlserver://ss.host;serverName=althost;DatabaseName=ssdb;"                                  | null     | "sqlserver://ss.host:1433"                                         | "mssql"      | null          | null          | "ss.host"                                   | 1433  | null                               | "ssdb"
    "jdbc:microsoft:sqlserver://ss.host:44;DatabaseName=ssdb;user=ssuser;password=pw;user=ssuser2;"   | null     | "microsoft:sqlserver://ss.host:44"                                 | "mssql"      | "sqlserver"   | "ssuser"      | "ss.host"                                   | 44    | null                               | "ssdb"

    // http://jtds.sourceforge.net/faq.html#urlFormat
    "jdbc:jtds:sqlserver://ss.host/ssdb"                                                              | null     | "jtds:sqlserver://ss.host:1433"                                    | "mssql"      | "sqlserver"   | null          | "ss.host"                                   | 1433  | null                               | "ssdb"
    "jdbc:jtds:sqlserver://ss.host:1433/ssdb"                                                         | null     | "jtds:sqlserver://ss.host:1433"                                    | "mssql"      | "sqlserver"   | null          | "ss.host"                                   | 1433  | null                               | "ssdb"
    "jdbc:jtds:sqlserver://ss.host:1433/ssdb;user=ssuser"                                             | null     | "jtds:sqlserver://ss.host:1433"                                    | "mssql"      | "sqlserver"   | "ssuser"      | "ss.host"                                   | 1433  | null                               | "ssdb"
    "jdbc:jtds:sqlserver://ss.host:1433/ssdb;user=ssuser"                                             | null     | "jtds:sqlserver://ss.host:1433"                                    | "mssql"      | "sqlserver"   | "ssuser"      | "ss.host"                                   | 1433  | null                               | "ssdb"
    "jdbc:jtds:sqlserver://ss.host/ssdb;instance=ssinstance"                                          | null     | "jtds:sqlserver://ss.host:1433"                                    | "mssql"      | "sqlserver"   | null          | "ss.host"                                   | 1433  | "ssinstance"                       | "ssdb"
    "jdbc:jtds:sqlserver://ss.host:1444/ssdb;instance=ssinstance"                                     | null     | "jtds:sqlserver://ss.host:1444"                                    | "mssql"      | "sqlserver"   | null          | "ss.host"                                   | 1444  | "ssinstance"                       | "ssdb"
    "jdbc:jtds:sqlserver://ss.host:1433/ssdb;instance=ssinstance;user=ssuser"                         | null     | "jtds:sqlserver://ss.host:1433"                                    | "mssql"      | "sqlserver"   | "ssuser"      | "ss.host"                                   | 1433  | "ssinstance"                       | "ssdb"

    // https://docs.oracle.com/cd/B28359_01/java.111/b31224/urls.htm
    // https://docs.oracle.com/cd/B28359_01/java.111/b31224/jdbcthin.htm
    "jdbc:oracle:thin:orcluser/PW@localhost:55:orclsn"                                                | null     | "oracle:thin://localhost:55"                                       | "oracle"     | "thin"        | "orcluser"    | "localhost"                                 | 55    | "orclsn"                           | null
    "jdbc:oracle:thin:orcluser/PW@//orcl.host:55/orclsn"                                              | null     | "oracle:thin://orcl.host:55"                                       | "oracle"     | "thin"        | "orcluser"    | "orcl.host"                                 | 55    | "orclsn"                           | null
    "jdbc:oracle:thin:orcluser/PW@127.0.0.1:orclsn"                                                   | null     | "oracle:thin://127.0.0.1:1521"                                     | "oracle"     | "thin"        | "orcluser"    | "127.0.0.1"                                 | 1521  | "orclsn"                           | null
    "jdbc:oracle:thin:orcluser/PW@//orcl.host/orclsn"                                                 | null     | "oracle:thin://orcl.host:1521"                                     | "oracle"     | "thin"        | "orcluser"    | "orcl.host"                                 | 1521  | "orclsn"                           | null
    "jdbc:oracle:thin:@//orcl.host:55/orclsn"                                                         | null     | "oracle:thin://orcl.host:55"                                       | "oracle"     | "thin"        | null          | "orcl.host"                                 | 55    | "orclsn"                           | null
    "jdbc:oracle:thin:@ldap://orcl.host:55/some,cn=OracleContext,dc=com"                              | null     | "oracle:thin://orcl.host:55"                                       | "oracle"     | "thin"        | null          | "orcl.host"                                 | 55    | "some,cn=oraclecontext,dc=com"     | null
    "jdbc:oracle:thin:127.0.0.1:orclsn"                                                               | null     | "oracle:thin://127.0.0.1:1521"                                     | "oracle"     | "thin"        | null          | "127.0.0.1"                                 | 1521  | "orclsn"                           | null
    "jdbc:oracle:thin:orcl.host:orclsn"                                                               | stdProps | "oracle:thin://orcl.host:9999"                                     | "oracle"     | "thin"        | "stdUserName" | "orcl.host"                                 | 9999  | "orclsn"                           | "stdDatabaseName"
    "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST= 127.0.0.1 )(POR T= 666))" +
      "(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=orclsn)))"                                       | null     | "oracle:thin://127.0.0.1:1521"                                     | "oracle"     | "thin"        | null          | "127.0.0.1"                                 | 1521  | "orclsn"                           | null
    // https://docs.oracle.com/cd/B28359_01/java.111/b31224/instclnt.htm                                             
    "jdbc:oracle:drivertype:orcluser/PW@orcl.host:55/orclsn"                                          | null     | "oracle:drivertype://orcl.host:55"                                 | "oracle"     | "drivertype"  | "orcluser"    | "orcl.host"                                 | 55    | "orclsn"                           | null
    "jdbc:oracle:oci8:@"                                                                              | null     | "oracle:oci8:"                                                     | "oracle"     | "oci8"        | null          | null                                        | 1521  | null                               | null
    "jdbc:oracle:oci8:@"                                                                              | stdProps | "oracle:oci8://stdServerName:9999"                                 | "oracle"     | "oci8"        | "stdUserName" | "stdServerName"                             | 9999  | null                               | "stdDatabaseName"
    "jdbc:oracle:oci8:@orclsn"                                                                        | null     | "oracle:oci8:"                                                     | "oracle"     | "oci8"        | null          | null                                        | 1521  | "orclsn"                           | null
    "jdbc:oracle:oci:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)( HOST =  orcl.host )" +
      "( PORT = 55  ))(CONNECT_DATA=(SERVICE_NAME =orclsn  )))"                                       | null     | "oracle:oci://orcl.host:55"                                        | "oracle"     | "oci"         | null          | "orcl.host"                                 | 55    | "orclsn"                           | null

    // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_tjvjcccn.html                 
    // https://www.ibm.com/support/knowledgecenter/en/SSEPGG_10.5.0/com.ibm.db2.luw.apdv.java.doc/src                /tpc/imjcc_r0052342.html
    "jdbc:db2://db2.host"                                                                             | null     | "db2://db2.host:50000"                                             | "db2"        | null          | null          | "db2.host"                                  | 50000 | null                               | null
    "jdbc:db2://db2.host"                                                                             | stdProps | "db2://db2.host:9999"                                              | "db2"        | null          | "stdUserName" | "db2.host"                                  | 9999  | null                               | "stdDatabaseName"
    "jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;"                                          | null     | "db2://db2.host:77"                                                | "db2"        | null          | "db2user"     | "db2.host"                                  | 77    | "db2db"                            | null
    "jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;"                                          | stdProps | "db2://db2.host:77"                                                | "db2"        | null          | "db2user"     | "db2.host"                                  | 77    | "db2db"                            | "stdDatabaseName"
    "jdbc:as400://ashost:66/asdb:user=asuser;password=PW;"                                            | null     | "as400://ashost:66"                                                | "db2"        | null          | "asuser"      | "ashost"                                    | 66    | "asdb"                             | null

    // https://help.sap.com/viewer/0eec0d68141541d1b07893a39944924e/2.0.03/en-US/ff15928cf5594d78b841fbbe649f04b4.html
    "jdbc:sap://sap.host"                                                                             | null     | "sap://sap.host"                                                   | "sap"        | null          | null          | "sap.host"                                  | null  | null                               | null
    "jdbc:sap://sap.host"                                                                             | stdProps | "sap://sap.host:9999"                                              | "sap"        | null          | "stdUserName" | "sap.host"                                  | 9999  | null                               | "stdDatabaseName"
    "jdbc:sap://sap.host:88/?databaseName=sapdb&user=sapuser&password=PW"                             | null     | "sap://sap.host:88"                                                | "sap"        | null          | "sapuser"     | "sap.host"                                  | 88    | null                               | "sapdb"

    // TODO:
//    "jdbc:informix-sqli://infxhost:99/infxdb:INFORMIXSERVER=infxsn;user=infxuser;password=PW"       | null  | "informix-sqli"   | null | "infxuser"  | "infxhost"  | 99   | "infxdb"| null
//    "jdbc:informix-direct://infxdb:999;user=infxuser;password=PW"                                   | null  | "informix-direct" | null | "infxuser"  | "infxhost"  | 999  | "infxdb"| null

    // http://www.h2database.com/html/features.html#database_url
    "jdbc:h2:mem:"                                                                                    | null     | "h2:mem:"                                                          | "h2"         | "mem"         | null          | null                                        | null  | null                               | null
    "jdbc:h2:mem:"                                                                                    | stdProps | "h2:mem:"                                                          | "h2"         | "mem"         | "stdUserName" | null                                        | null  | null                               | "stdDatabaseName"
    "jdbc:h2:mem:h2db"                                                                                | null     | "h2:mem:"                                                          | "h2"         | "mem"         | null          | null                                        | null  | "h2db"                             | null
    "jdbc:h2:tcp://h2.host:111/path/h2db;user=h2user;password=PW"                                     | null     | "h2:tcp://h2.host:111"                                             | "h2"         | "tcp"         | "h2user"      | "h2.host"                                   | 111   | "path/h2db"                        | null
    "jdbc:h2:ssl://h2.host:111/path/h2db;user=h2user;password=PW"                                     | null     | "h2:ssl://h2.host:111"                                             | "h2"         | "ssl"         | "h2user"      | "h2.host"                                   | 111   | "path/h2db"                        | null
    "jdbc:h2:/data/h2file"                                                                            | null     | "h2:file:"                                                         | "h2"         | "file"        | null          | null                                        | null  | "/data/h2file"                     | null
    "jdbc:h2:file:~/h2file;USER=h2user;PASSWORD=PW"                                                   | null     | "h2:file:"                                                         | "h2"         | "file"        | null          | null                                        | null  | "~/h2file"                         | null
    "jdbc:h2:file:/data/h2file"                                                                       | null     | "h2:file:"                                                         | "h2"         | "file"        | null          | null                                        | null  | "/data/h2file"                     | null
    "jdbc:h2:file:C:/data/h2file"                                                                     | null     | "h2:file:"                                                         | "h2"         | "file"        | null          | null                                        | null  | "c:/data/h2file"                   | null
    "jdbc:h2:zip:~/db.zip!/h2zip"                                                                     | null     | "h2:zip:"                                                          | "h2"         | "zip"         | null          | null                                        | null  | "~/db.zip!/h2zip"                  | null

    // http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html
    "jdbc:hsqldb:hsdb"                                                                                | null     | "hsqldb:mem:"                                                      | "hsqldb"     | "mem"         | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:hsdb"                                                                                | stdProps | "hsqldb:mem:"                                                      | "hsqldb"     | "mem"         | "stdUserName" | null                                        | null  | "hsdb"                             | "stdDatabaseName"
    "jdbc:hsqldb:mem:hsdb"                                                                            | null     | "hsqldb:mem:"                                                      | "hsqldb"     | "mem"         | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:mem:hsdb;shutdown=true"                                                              | null     | "hsqldb:mem:"                                                      | "hsqldb"     | "mem"         | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:mem:hsdb?shutdown=true"                                                              | null     | "hsqldb:mem:"                                                      | "hsqldb"     | "mem"         | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:file:hsdb"                                                                           | null     | "hsqldb:file:"                                                     | "hsqldb"     | "file"        | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:file:hsdb;user=aUserName;password=3xLVz"                                             | null     | "hsqldb:file:"                                                     | "hsqldb"     | "file"        | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:file:hsdb;create=false?user=aUserName&password=3xLVz"                                | null     | "hsqldb:file:"                                                     | "hsqldb"     | "file"        | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:file:/loc/hsdb"                                                                      | null     | "hsqldb:file:"                                                     | "hsqldb"     | "file"        | "SA"          | null                                        | null  | "/loc/hsdb"                        | null
    "jdbc:hsqldb:file:C:/hsdb"                                                                        | null     | "hsqldb:file:"                                                     | "hsqldb"     | "file"        | "SA"          | null                                        | null  | "c:/hsdb"                          | null
    "jdbc:hsqldb:res:hsdb"                                                                            | null     | "hsqldb:res:"                                                      | "hsqldb"     | "res"         | "SA"          | null                                        | null  | "hsdb"                             | null
    "jdbc:hsqldb:res:/cp/hsdb"                                                                        | null     | "hsqldb:res:"                                                      | "hsqldb"     | "res"         | "SA"          | null                                        | null  | "/cp/hsdb"                         | null
    "jdbc:hsqldb:hsql://hs.host:333/hsdb"                                                             | null     | "hsqldb:hsql://hs.host:333"                                        | "hsqldb"     | "hsql"        | "SA"          | "hs.host"                                   | 333   | "hsdb"                             | null
    "jdbc:hsqldb:hsqls://hs.host/hsdb"                                                                | null     | "hsqldb:hsqls://hs.host:9001"                                      | "hsqldb"     | "hsqls"       | "SA"          | "hs.host"                                   | 9001  | "hsdb"                             | null
    "jdbc:hsqldb:http://hs.host"                                                                      | null     | "hsqldb:http://hs.host:80"                                         | "hsqldb"     | "http"        | "SA"          | "hs.host"                                   | 80    | null                               | null
    "jdbc:hsqldb:http://hs.host:333/hsdb"                                                             | null     | "hsqldb:http://hs.host:333"                                        | "hsqldb"     | "http"        | "SA"          | "hs.host"                                   | 333   | "hsdb"                             | null
    "jdbc:hsqldb:https://127.0.0.1/hsdb"                                                              | null     | "hsqldb:https://127.0.0.1:443"                                     | "hsqldb"     | "https"       | "SA"          | "127.0.0.1"                                 | 443   | "hsdb"                             | null

    // https://db.apache.org/derby/papers/DerbyClientSpec.html#Connection+URL+Format
    // https://db.apache.org/derby/docs/10.8/devguide/cdevdvlp34964.html
    "jdbc:derby:derbydb"                                                                              | null     | "derby:directory:"                                                 | "derby"      | "directory"   | "APP"         | null                                        | null  | "derbydb"                          | null
    "jdbc:derby:derbydb"                                                                              | stdProps | "derby:directory:"                                                 | "derby"      | "directory"   | "stdUserName" | null                                        | null  | "derbydb"                          | "stdDatabaseName"
    "jdbc:derby:derbydb;user=derbyuser;password=pw"                                                   | null     | "derby:directory:"                                                 | "derby"      | "directory"   | "derbyuser"   | null                                        | null  | "derbydb"                          | null
    "jdbc:derby:memory:derbydb"                                                                       | null     | "derby:memory:"                                                    | "derby"      | "memory"      | "APP"         | null                                        | null  | "derbydb"                          | null
    "jdbc:derby:memory:;databaseName=derbydb"                                                         | null     | "derby:memory:"                                                    | "derby"      | "memory"      | "APP"         | null                                        | null  | null                               | "derbydb"
    "jdbc:derby:memory:derbydb;databaseName=altdb"                                                    | null     | "derby:memory:"                                                    | "derby"      | "memory"      | "APP"         | null                                        | null  | "derbydb"                          | "altdb"
    "jdbc:derby:memory:derbydb;user=derbyuser;password=pw"                                            | null     | "derby:memory:"                                                    | "derby"      | "memory"      | "derbyuser"   | null                                        | null  | "derbydb"                          | null
    "jdbc:derby://derby.host:222/memory:derbydb;create=true"                                          | null     | "derby:network://derby.host:222"                                   | "derby"      | "network"     | "APP"         | "derby.host"                                | 222   | "derbydb"                          | null
    "jdbc:derby://derby.host/memory:derbydb;create=true;user=derbyuser;password=pw"                   | null     | "derby:network://derby.host:1527"                                  | "derby"      | "network"     | "derbyuser"   | "derby.host"                                | 1527  | "derbydb"                          | null
    "jdbc:derby://127.0.0.1:1527/memory:derbydb;create=true;user=derbyuser;password=pw"               | null     | "derby:network://127.0.0.1:1527"                                   | "derby"      | "network"     | "derbyuser"   | "127.0.0.1"                                 | 1527  | "derbydb"                          | null
    "jdbc:derby:directory:derbydb;user=derbyuser;password=pw"                                         | null     | "derby:directory:"                                                 | "derby"      | "directory"   | "derbyuser"   | null                                        | null  | "derbydb"                          | null
    "jdbc:derby:classpath:/some/derbydb;user=derbyuser;password=pw"                                   | null     | "derby:classpath:"                                                 | "derby"      | "classpath"   | "derbyuser"   | null                                        | null  | "/some/derbydb"                    | null
    "jdbc:derby:jar:/derbydb;user=derbyuser;password=pw"                                              | null     | "derby:jar:"                                                       | "derby"      | "jar"         | "derbyuser"   | null                                        | null  | "/derbydb"                         | null
    "jdbc:derby:jar:(~/path/to/db.jar)/other/derbydb;user=derbyuser;password=pw"                      | null     | "derby:jar:"                                                       | "derby"      | "jar"         | "derbyuser"   | null                                        | null  | "(~/path/to/db.jar)/other/derbydb" | null

    expected = DbInfo.builder().system(system).subtype(subtype).user(user).name(name).db(db).host(host).port(port).shortUrl(shortUrl).build()
  }
}
