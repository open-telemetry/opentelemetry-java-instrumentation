import datadog.trace.instrumentation.jdbc.DBInfo
import spock.lang.Shared
import spock.lang.Specification

import static datadog.trace.instrumentation.jdbc.JDBCConnectionUrlParser.parse

class JDBCConnectionUrlParserTest extends Specification {

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
    parse(url, null) == DBInfo.DEFAULT

    where:
    url            | _
    null           | _
    ""             | _
    "jdbc:"        | _
    "jdbc::"       | _
    "bogus:string" | _
  }

  def "verify #type:#subtype parsing of #url"() {
    setup:
    def info = parse(url, props)

    expect:
    info.url == expected.url
    info.type == expected.type
    info.host == expected.host
    info.port == expected.port
    info.user == expected.user
    info.instance == expected.instance

    info == expected

    where:
    url                                                                                               | props    | type         | subtype       | user          | host                                      | port  | instance                           | db
    // https://jdbc.postgresql.org/documentation/94/connect.html
    "jdbc:postgresql:///"                                                                             | null     | "postgresql" | null          | null          | "localhost"                               | 5432  | null                               | null
    "jdbc:postgresql:///"                                                                             | stdProps | "postgresql" | null          | "stdUserName" | "stdServerName"                           | 9999  | null                               | "stdDatabaseName"
    "jdbc:postgresql://pg.host"                                                                       | null     | "postgresql" | null          | null          | "pg.host"                                 | 5432  | null                               | null
    "jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW"                                       | null     | "postgresql" | null          | "pguser"      | "pg.host"                                 | 11    | null                               | "pgdb"
    "jdbc:postgresql://pg.host:11/pgdb?user=pguser&password=PW"                                       | stdProps | "postgresql" | null          | "pguser"      | "pg.host"                                 | 11    | null                               | "pgdb"

    // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
    // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
    "jdbc:mysql:///"                                                                                  | null     | "mysql"      | null          | null          | "localhost"                               | 3306  | null                               | null
    "jdbc:mysql:///"                                                                                  | stdProps | "mysql"      | null          | "stdUserName" | "stdServerName"                           | 9999  | null                               | "stdDatabaseName"
    "jdbc:mysql://my.host"                                                                            | null     | "mysql"      | null          | null          | "my.host"                                 | 3306  | null                               | null
    "jdbc:mysql://my.host?user=myuser&password=PW"                                                    | null     | "mysql"      | null          | "myuser"      | "my.host"                                 | 3306  | null                               | null
    "jdbc:mysql://my.host:22/mydb?user=myuser&password=PW"                                            | null     | "mysql"      | null          | "myuser"      | "my.host"                                 | 22    | null                               | "mydb"
    "jdbc:mysql://127.0.0.1:22/mydb?user=myuser&password=PW"                                          | stdProps | "mysql"      | null          | "myuser"      | "127.0.0.1"                               | 22    | null                               | "mydb"

    // https://mariadb.com/kb/en/library/about-mariadb-connector-j/#connection-strings
    "jdbc:mariadb:127.0.0.1:33/mdbdb"                                                                 | null     | "mariadb"    | null          | null          | "127.0.0.1"                               | 33    | null                               | "mdbdb"
    "jdbc:mariadb:localhost/mdbdb"                                                                    | null     | "mariadb"    | null          | null          | "localhost"                               | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:localhost/mdbdb?user=mdbuser&password=PW"                                           | stdProps | "mariadb"    | null          | "mdbuser"     | "localhost"                               | 9999  | null                               | "mdbdb"
    "jdbc:mariadb:localhost:33/mdbdb"                                                                 | stdProps | "mariadb"    | null          | "stdUserName" | "localhost"                               | 33    | null                               | "mdbdb"
    "jdbc:mariadb://mdb.host:33/mdbdb?user=mdbuser&password=PW"                                       | null     | "mariadb"    | null          | "mdbuser"     | "mdb.host"                                | 33    | null                               | "mdbdb"
    "jdbc:mariadb:aurora://mdb.host/mdbdb"                                                            | null     | "mariadb"    | "aurora"      | null          | "mdb.host"                                | 3306  | null                               | "mdbdb"
    "jdbc:mysql:aurora://mdb.host/mdbdb"                                                              | null     | "mysql"      | "aurora"      | null          | "mdb.host"                                | 3306  | null                               | "mdbdb"
    "jdbc:mysql:failover://localhost/mdbdb?autoReconnect=true"                                        | null     | "mysql"      | "failover"    | null          | "localhost"                               | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:failover://mdb.host1:33,mdb.host/mdbdb?characterEncoding=utf8"                      | null     | "mariadb"    | "failover"    | null          | "mdb.host1"                               | 33    | null                               | "mdbdb"
    "jdbc:mariadb:sequential://mdb.host1,mdb.host2:33/mdbdb"                                          | null     | "mariadb"    | "sequential"  | null          | "mdb.host1"                               | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:loadbalance://127.0.0.1:33,mdb.host/mdbdb"                                          | null     | "mariadb"    | "loadbalance" | null          | "127.0.0.1"                               | 33    | null                               | "mdbdb"
    "jdbc:mariadb:loadbalance://[2001:0660:7401:0200:0000:0000:0edf:bdd7]:33,mdb.host/mdbdb"          | null     | "mariadb"    | "loadbalance" | null          | "2001:0660:7401:0200:0000:0000:0edf:bdd7" | 33    | null                               | "mdbdb"
    "jdbc:mysql:loadbalance://127.0.0.1,127.0.0.1:3306/mdbdb?user=mdbuser&password=PW"                | null     | "mysql"      | "loadbalance" | "mdbuser"     | "127.0.0.1"                               | 3306  | null                               | "mdbdb"
    "jdbc:mariadb:replication://localhost:33,anotherhost:3306/mdbdb"                                  | null     | "mariadb"    | "replication" | null          | "localhost"                               | 33    | null                               | "mdbdb"
    "jdbc:mysql:replication://address=(HOST=127.0.0.1)(port=33)(user=mdbuser)(password=PW)," +
      "address=(host=mdb.host)(port=3306)(user=otheruser)(password=PW)/mdbdb?user=wrong&password=PW"  | null     | "mysql"      | "replication" | "mdbuser"     | "127.0.0.1"                               | 33    | null                               | "mdbdb"
    "jdbc:mysql:replication://address=(HOST=mdb.host)," +
      "address=(host=anotherhost)(port=3306)(user=wrong)(password=PW)/mdbdb?user=mdbuser&password=PW" | null     | "mysql"      | "replication" | "mdbuser"     | "mdb.host"                                | 3306  | null                               | "mdbdb"

    //https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
    "jdbc:microsoft:sqlserver://;"                                                                    | null     | "sqlserver"  | null          | null          | "localhost"                               | 1433  | null                               | null
    "jdbc:microsoft:sqlserver://;"                                                                    | stdProps | "sqlserver"  | null          | "stdUserName" | "stdServerName"                           | 9999  | null                               | "stdDatabaseName"
    "jdbc:sqlserver://ss.host\\ssinstance:44;databaseName=ssdb;user=ssuser;password=pw"               | null     | "sqlserver"  | null          | "ssuser"      | "ss.host"                                 | 44    | "ssinstance"                       | "ssdb"
    "jdbc:sqlserver://;serverName=ss.host\\ssinstance:44;DatabaseName=;"                              | null     | "sqlserver"  | null          | null          | "ss.host"                                 | 44    | "ssinstance"                       | null
    "jdbc:sqlserver://ss.host;serverName=althost;DatabaseName=ssdb;"                                  | null     | "sqlserver"  | null          | null          | "ss.host"                                 | 1433  | null                               | "ssdb"
    "jdbc:microsoft:sqlserver://ss.host:44;DatabaseName=ssdb;user=ssuser;password=pw;user=ssuser2;"   | null     | "sqlserver"  | null          | "ssuser"      | "ss.host"                                 | 44    | null                               | "ssdb"

    // https://docs.oracle.com/cd/B28359_01/java.111/b31224/urls.htm
    // https://docs.oracle.com/cd/B28359_01/java.111/b31224/jdbcthin.htm
    "jdbc:oracle:thin:orcluser/PW@localhost:55:orclsn"                                                | null     | "oracle"     | "thin"        | "orcluser"    | "localhost"                               | 55    | "orclsn"                           | null
    "jdbc:oracle:thin:orcluser/PW@//orcl.host:55/orclsn"                                              | null     | "oracle"     | "thin"        | "orcluser"    | "orcl.host"                               | 55    | "orclsn"                           | null
    "jdbc:oracle:thin:orcluser/PW@127.0.0.1:orclsn"                                                   | null     | "oracle"     | "thin"        | "orcluser"    | "127.0.0.1"                               | 1521  | "orclsn"                           | null
    "jdbc:oracle:thin:orcluser/PW@//orcl.host/orclsn"                                                 | null     | "oracle"     | "thin"        | "orcluser"    | "orcl.host"                               | 1521  | "orclsn"                           | null
    "jdbc:oracle:thin:@//orcl.host:55/orclsn"                                                         | null     | "oracle"     | "thin"        | null          | "orcl.host"                               | 55    | "orclsn"                           | null
    "jdbc:oracle:thin:@ldap://orcl.host:55/some,cn=OracleContext,dc=com"                              | null     | "oracle"     | "thin"        | null          | "orcl.host"                               | 55    | "some,cn=oraclecontext,dc=com"     | null
    "jdbc:oracle:thin:127.0.0.1:orclsn"                                                               | null     | "oracle"     | "thin"        | null          | "127.0.0.1"                               | 1521  | "orclsn"                           | null
    "jdbc:oracle:thin:orcl.host:orclsn"                                                               | stdProps | "oracle"     | "thin"        | "stdUserName" | "orcl.host"                               | 9999  | "orclsn"                           | "stdDatabaseName"
    "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST= 127.0.0.1 )(POR T= 666))" +
      "(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=orclsn)))"                                       | null     | "oracle"     | "thin"        | null          | "127.0.0.1"                               | 1521  | "orclsn"                           | null
    // https://docs.oracle.com/cd/B28359_01/java.111/b31224/instclnt.htm
    "jdbc:oracle:drivertype:orcluser/PW@orcl.host:55/orclsn"                                          | null     | "oracle"     | "drivertype"  | "orcluser"    | "orcl.host"                               | 55    | "orclsn"                           | null
    "jdbc:oracle:oci8:@"                                                                              | null     | "oracle"     | "oci8"        | null          | null                                      | 1521  | null                               | null
    "jdbc:oracle:oci8:@"                                                                              | stdProps | "oracle"     | "oci8"        | "stdUserName" | "stdServerName"                           | 9999  | null                               | "stdDatabaseName"
    "jdbc:oracle:oci8:@orclsn"                                                                        | null     | "oracle"     | "oci8"        | null          | null                                      | 1521  | "orclsn"                           | null
    "jdbc:oracle:oci:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)( HOST =  orcl.host )" +
      "( PORT = 55  ))(CONNECT_DATA=(SERVICE_NAME =orclsn  )))"                                       | null     | "oracle"     | "oci"         | null          | "orcl.host"                               | 55    | "orclsn"                           | null

    // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_tjvjcccn.html
    // https://www.ibm.com/support/knowledgecenter/en/SSEPGG_10.5.0/com.ibm.db2.luw.apdv.java.doc/src/tpc/imjcc_r0052342.html
    "jdbc:db2://db2.host"                                                                             | null     | "db2"        | null          | null          | "db2.host"                                | 50000 | null                               | null
    "jdbc:db2://db2.host"                                                                             | stdProps | "db2"        | null          | "stdUserName" | "db2.host"                                | 9999  | null                               | "stdDatabaseName"
    "jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;"                                          | null     | "db2"        | null          | "db2user"     | "db2.host"                                | 77    | "db2db"                            | null
    "jdbc:db2://db2.host:77/db2db:user=db2user;password=PW;"                                          | stdProps | "db2"        | null          | "db2user"     | "db2.host"                                | 77    | "db2db"                            | "stdDatabaseName"
    "jdbc:as400://ashost:66/asdb:user=asuser;password=PW;"                                            | null     | "as400"      | null          | "asuser"      | "ashost"                                  | 66    | "asdb"                             | null

    // https://help.sap.com/viewer/0eec0d68141541d1b07893a39944924e/2.0.03/en-US/ff15928cf5594d78b841fbbe649f04b4.html
    "jdbc:sap://sap.host"                                                                             | null     | "sap"        | null          | null          | "sap.host"                                | null  | null                               | null
    "jdbc:sap://sap.host"                                                                             | stdProps | "sap"        | null          | "stdUserName" | "sap.host"                                | 9999  | null                               | "stdDatabaseName"
    "jdbc:sap://sap.host:88/?databaseName=sapdb&user=sapuser&password=PW"                             | null     | "sap"        | null          | "sapuser"     | "sap.host"                                | 88    | null                               | "sapdb"

    // TODO:
//    "jdbc:informix-sqli://infxhost:99/infxdb:INFORMIXSERVER=infxsn;user=infxuser;password=PW"    | null  | "informix-sqli"   | null | "infxuser"  | "infxhost"  | 99   | "infxdb"| null
//    "jdbc:informix-direct://infxdb:999;user=infxuser;password=PW"                                | null  | "informix-direct" | null | "infxuser"  | "infxhost"  | 999  | "infxdb"| null

    // http://www.h2database.com/html/features.html#database_url
    "jdbc:h2:mem:"                                                                                    | null     | "h2"         | "mem"         | null          | null                                      | null  | null                               | null
    "jdbc:h2:mem:"                                                                                    | stdProps | "h2"         | "mem"         | "stdUserName" | "stdServerName"                           | 9999  | null                               | "stdDatabaseName"
    "jdbc:h2:mem:h2db"                                                                                | null     | "h2"         | "mem"         | null          | null                                      | null  | "h2db"                             | null
    "jdbc:h2:tcp://h2.host:111/path/h2db;user=h2user;password=PW"                                     | null     | "h2"         | "tcp"         | "h2user"      | "h2.host"                                 | 111   | "path/h2db"                        | null
    "jdbc:h2:ssl://h2.host:111/path/h2db;user=h2user;password=PW"                                     | null     | "h2"         | "ssl"         | "h2user"      | "h2.host"                                 | 111   | "path/h2db"                        | null
    "jdbc:h2:/data/h2file"                                                                            | null     | "h2"         | "file"        | null          | null                                      | null  | "/data/h2file"                     | null
    "jdbc:h2:file:~/h2file;USER=h2user;PASSWORD=PW"                                                   | null     | "h2"         | "file"        | null          | null                                      | null  | "~/h2file"                         | null
    "jdbc:h2:file:/data/h2file"                                                                       | null     | "h2"         | "file"        | null          | null                                      | null  | "/data/h2file"                     | null
    "jdbc:h2:file:C:/data/h2file"                                                                     | null     | "h2"         | "file"        | null          | null                                      | null  | "c:/data/h2file"                   | null
    "jdbc:h2:zip:~/db.zip!/h2zip"                                                                     | null     | "h2"         | "zip"         | null          | null                                      | null  | "~/db.zip!/h2zip"                  | null

    // http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html
    "jdbc:hsqldb:hsdb"                                                                                | null     | "hsqldb"     | "mem"         | "SA"          | null                                      | null  | "hsdb"                             | null
    "jdbc:hsqldb:hsdb"                                                                                | stdProps | "hsqldb"     | "mem"         | "stdUserName" | "stdServerName"                           | 9999  | "hsdb"                             | "stdDatabaseName"
    "jdbc:hsqldb:mem:hsdb"                                                                            | null     | "hsqldb"     | "mem"         | "SA"          | null                                      | null  | "hsdb"                             | null
    "jdbc:hsqldb:file:hsdb"                                                                           | null     | "hsqldb"     | "file"        | "SA"          | null                                      | null  | "hsdb"                             | null
    "jdbc:hsqldb:file:/loc/hsdb"                                                                      | null     | "hsqldb"     | "file"        | "SA"          | null                                      | null  | "/loc/hsdb"                        | null
    "jdbc:hsqldb:file:C:/hsdb"                                                                        | null     | "hsqldb"     | "file"        | "SA"          | null                                      | null  | "c:/hsdb"                          | null
    "jdbc:hsqldb:res:hsdb"                                                                            | null     | "hsqldb"     | "res"         | "SA"          | null                                      | null  | "hsdb"                             | null
    "jdbc:hsqldb:res:/cp/hsdb"                                                                        | null     | "hsqldb"     | "res"         | "SA"          | null                                      | null  | "/cp/hsdb"                         | null
    "jdbc:hsqldb:hsql://hs.host:333/hsdb"                                                             | null     | "hsqldb"     | "hsql"        | "SA"          | "hs.host"                                 | 333   | "hsdb"                             | null
    "jdbc:hsqldb:hsqls://hs.host/hsdb"                                                                | null     | "hsqldb"     | "hsqls"       | "SA"          | "hs.host"                                 | 9001  | "hsdb"                             | null
    "jdbc:hsqldb:http://hs.host"                                                                      | null     | "hsqldb"     | "http"        | "SA"          | "hs.host"                                 | 80    | null                               | null
    "jdbc:hsqldb:http://hs.host:333/hsdb"                                                             | null     | "hsqldb"     | "http"        | "SA"          | "hs.host"                                 | 333   | "hsdb"                             | null
    "jdbc:hsqldb:https://127.0.0.1/hsdb"                                                              | null     | "hsqldb"     | "https"       | "SA"          | "127.0.0.1"                               | 443   | "hsdb"                             | null

    // https://db.apache.org/derby/papers/DerbyClientSpec.html#Connection+URL+Format
    // https://db.apache.org/derby/docs/10.8/devguide/cdevdvlp34964.html
    "jdbc:derby:derbydb"                                                                              | null     | "derby"      | "directory"   | "APP"         | null                                      | null  | "derbydb"                          | null
    "jdbc:derby:derbydb"                                                                              | stdProps | "derby"      | "directory"   | "stdUserName" | "stdServerName"                           | 9999  | "derbydb"                          | "stdDatabaseName"
    "jdbc:derby:derbydb;user=derbyuser;password=pw"                                                   | null     | "derby"      | "directory"   | "derbyuser"   | null                                      | null  | "derbydb"                          | null
    "jdbc:derby:memory:derbydb"                                                                       | null     | "derby"      | "memory"      | "APP"         | null                                      | null  | "derbydb"                          | null
    "jdbc:derby:memory:;databaseName=derbydb"                                                         | null     | "derby"      | "memory"      | "APP"         | null                                      | null  | null                               | "derbydb"
    "jdbc:derby:memory:derbydb;databaseName=altdb"                                                    | null     | "derby"      | "memory"      | "APP"         | null                                      | null  | "derbydb"                          | "altdb"
    "jdbc:derby:memory:derbydb;user=derbyuser;password=pw"                                            | null     | "derby"      | "memory"      | "derbyuser"   | null                                      | null  | "derbydb"                          | null
    "jdbc:derby://derby.host:222/memory:derbydb;create=true"                                          | null     | "derby"      | "network"     | "APP"         | "derby.host"                              | 222   | "derbydb"                          | null
    "jdbc:derby://derby.host/memory:derbydb;create=true;user=derbyuser;password=pw"                   | null     | "derby"      | "network"     | "derbyuser"   | "derby.host"                              | 1527  | "derbydb"                          | null
    "jdbc:derby://127.0.0.1:1527/memory:derbydb;create=true;user=derbyuser;password=pw"               | null     | "derby"      | "network"     | "derbyuser"   | "127.0.0.1"                               | 1527  | "derbydb"                          | null
    "jdbc:derby:directory:derbydb;user=derbyuser;password=pw"                                         | null     | "derby"      | "directory"   | "derbyuser"   | null                                      | null  | "derbydb"                          | null
    "jdbc:derby:classpath:/some/derbydb;user=derbyuser;password=pw"                                   | null     | "derby"      | "classpath"   | "derbyuser"   | null                                      | null  | "/some/derbydb"                    | null
    "jdbc:derby:jar:/derbydb;user=derbyuser;password=pw"                                              | null     | "derby"      | "jar"         | "derbyuser"   | null                                      | null  | "/derbydb"                         | null
    "jdbc:derby:jar:(~/path/to/db.jar)/other/derbydb;user=derbyuser;password=pw"                      | null     | "derby"      | "jar"         | "derbyuser"   | null                                      | null  | "(~/path/to/db.jar)/other/derbydb" | null

    expected = new DBInfo.Builder().type(type).subtype(subtype).user(user).instance(instance).db(db).host(host).port(port).build()
  }
}
