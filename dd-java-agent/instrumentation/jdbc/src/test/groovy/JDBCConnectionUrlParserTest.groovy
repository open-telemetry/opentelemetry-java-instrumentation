import datadog.trace.instrumentation.jdbc.JDBCMaps
import spock.lang.Specification

import static datadog.trace.instrumentation.jdbc.JDBCConnectionUrlParser.parse

class JDBCConnectionUrlParserTest extends Specification {

  def "invalid url returns default"() {
    expect:
    parse(url, null) == JDBCMaps.DBInfo.DEFAULT

    where:
    url            | _
    null           | _
    ""             | _
    "bogus:string" | _
  }

  def "verify #format parsing of #url"() {
    setup:
    def info = parse(url, null)

    expect:
    info.url == expected.url
    info.type == expected.type
    info.host == expected.host
    info.port == expected.port
    info.user == expected.user
    info.instance == expected.instance

    info == expected

    where:
    url                                                                                            | format            | user        | host        | port | instance
    // https://jdbc.postgresql.org/documentation/94/connect.html
    "jdbc:postgresql:///"                                                                          | "postgresql"      | null        | "localhost" | 5432 | ""
    "jdbc:postgresql://pghost"                                                                     | "postgresql"      | null        | "pghost"    | 5432 | ""
    "jdbc:postgresql://pghost:11/pgdb?user=pguser&password=PW"                                     | "postgresql"      | "pguser"    | "pghost"    | 11   | "pgdb"

    // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
    "jdbc:mysql:///"                                                                               | "mysql"           | null        | "localhost" | 3306 | ""
    "jdbc:mysql://myhost"                                                                          | "mysql"           | null        | "myhost"    | 3306 | ""
    "jdbc:mysql://myhost?user=myuser&password=PW"                                                  | "mysql"           | "myuser"    | "myhost"    | 3306 | ""
    "jdbc:mysql://myhost:22/mydb?user=myuser&password=PW"                                          | "mysql"           | "myuser"    | "myhost"    | 22   | "mydb"
    "jdbc:mariadb://mdbhost:33/mdbdb?user=mdbuser&password=PW"                                     | "mariadb"         | "mdbuser"   | "mdbhost"   | 33   | "mdbdb"

    //https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
    "jdbc:microsoft:sqlserver://;"                                                                 | "sqlserver"       | null        | "localhost" | 1433 | "MSSQLSERVER"
    "jdbc:sqlserver://sshost\\ssinstance:44;databaseName=ssdb;user=ssuser;password=pw"             | "sqlserver"       | "ssuser"    | "sshost"    | 44   | "ssinstance"
    "jdbc:sqlserver://;serverName=sshost\\ssinstance:44;DatabaseName=;"                            | "sqlserver"       | null        | "sshost"    | 44   | "ssinstance"
    "jdbc:sqlserver://sshost;serverName=althost;DatabaseName=ssdb;"                                | "sqlserver"       | null        | "sshost"    | 1433 | "MSSQLSERVER"
    "jdbc:microsoft:sqlserver://sshost:44;DatabaseName=ssdb;user=ssuser;password=pw;user=ssuser2;" | "sqlserver"       | "ssuser"    | "sshost"    | 44   | "MSSQLSERVER"

    // TODO:
//    "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST= localhost )(PORT= 1521))" +
//      "(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=orcl)))"                                      | "oracle"          | "orcluser"  | "orclhost"  | 55   | "orcldb"
//    "jdbc:oracle:drivertype:orcluser/PW@orclhost:55/orcldb"                                        | "oracle"          | "orcluser"  | "orclhost"  | 55   | "orcldb"

    // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_tjvjcccn.html
    // https://www.ibm.com/support/knowledgecenter/en/SSEPGG_10.5.0/com.ibm.db2.luw.apdv.java.doc/src/tpc/imjcc_r0052342.html
    "jdbc:as400://ashost:66/asdb:user=asuser;password=PW;"                                         | "as400"           | "asuser"    | "ashost"    | 66   | "asdb"
    "jdbc:db2://db2host:77/db2db:user=db2user;password=PW;"                                        | "db2"             | "db2user"   | "db2host"   | 77   | "db2db"

    // https://help.sap.com/viewer/0eec0d68141541d1b07893a39944924e/2.0.03/en-US/ff15928cf5594d78b841fbbe649f04b4.html
    "jdbc:sap://saphost:88/?databaseName=sapdb&user=sapuser&password=PW"                           | "sap"             | "sapuser"   | "saphost"   | 88   | "sapdb"

    // TODO:
//    "jdbc:informix-sqli://infxhost:99/infxdb:INFORMIXSERVER=infxsn;user=infxuser;password=PW"      | "informix-sqli"   | "infxuser"  | "infxhost"  | 99   | "infxdb"
//    "jdbc:informix-direct://infxdb:999;user=infxuser;password=PW"                                  | "informix-direct" | "infxuser"  | "infxhost"  | 999  | "infxdb"

    // http://www.h2database.com/html/features.html#database_url
    "jdbc:h2:mem:"                                                                                 | "h2:mem"          | null        | null        | null | ""
    "jdbc:h2:mem:h2db"                                                                             | "h2:mem"          | null        | null        | null | "h2db"
    "jdbc:h2:tcp://h2host:111/path/h2db;user=h2user;password=PW"                                   | "h2:tcp"          | "h2user"    | "h2host"    | 111  | "path/h2db"
    "jdbc:h2:ssl://h2host:111/path/h2db;user=h2user;password=PW"                                   | "h2:ssl"          | "h2user"    | "h2host"    | 111  | "path/h2db"
    "jdbc:h2:/data/h2file"                                                                         | "h2:file"         | null        | null        | null | "/data/h2file"
    "jdbc:h2:file:~/h2file;USER=h2user;PASSWORD=PW"                                                | "h2:file"         | null        | null        | null | "~/h2file"
    "jdbc:h2:file:/data/h2file"                                                                    | "h2:file"         | null        | null        | null | "/data/h2file"
    "jdbc:h2:file:C:/data/h2file"                                                                  | "h2:file"         | null        | null        | null | "c:/data/h2file"
    "jdbc:h2:zip:~/db.zip!/h2zip"                                                                  | "h2:zip"          | null        | null        | null | "~/db.zip!/h2zip"

    // http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html
    "jdbc:hsqldb:hsdb"                                                                             | "hsqldb:mem"      | "SA"        | null        | null | "hsdb"
    "jdbc:hsqldb:mem:hsdb"                                                                         | "hsqldb:mem"      | "SA"        | null        | null | "hsdb"
    "jdbc:hsqldb:file:hsdb"                                                                        | "hsqldb:file"     | "SA"        | null        | null | "hsdb"
    "jdbc:hsqldb:file:/loc/hsdb"                                                                   | "hsqldb:file"     | "SA"        | null        | null | "/loc/hsdb"
    "jdbc:hsqldb:file:C:/hsdb"                                                                     | "hsqldb:file"     | "SA"        | null        | null | "c:/hsdb"
    "jdbc:hsqldb:res:hsdb"                                                                         | "hsqldb:res"      | "SA"        | null        | null | "hsdb"
    "jdbc:hsqldb:res:/cp/hsdb"                                                                     | "hsqldb:res"      | "SA"        | null        | null | "/cp/hsdb"
    "jdbc:hsqldb:hsql://hshost:333/hsdb"                                                           | "hsqldb:hsql"     | "SA"        | "hshost"    | 333  | "hsdb"
    "jdbc:hsqldb:hsqls://hshost/hsdb"                                                              | "hsqldb:hsqls"    | "SA"        | "hshost"    | 9001 | "hsdb"
    "jdbc:hsqldb:http://hshost"                                                                    | "hsqldb:http"     | "SA"        | "hshost"    | 80   | null
    "jdbc:hsqldb:http://hshost:333/hsdb"                                                           | "hsqldb:http"     | "SA"        | "hshost"    | 333  | "hsdb"
    "jdbc:hsqldb:https://127.0.0.1/hsdb"                                                           | "hsqldb:https"    | "SA"        | "127.0.0.1" | 443  | "hsdb"

    // https://db.apache.org/derby/papers/DerbyClientSpec.html#Connection+URL+Format
    // https://db.apache.org/derby/docs/10.8/devguide/cdevdvlp34964.html
    "jdbc:derby:derbydb"                                                                           | "derby:directory" | "APP"       | null        | null | "derbydb"
    "jdbc:derby:derbydb;user=derbyuser;password=pw"                                                | "derby:directory" | "derbyuser" | null        | null | "derbydb"
    "jdbc:derby:memory:derbydb"                                                                    | "derby:memory"    | "APP"       | null        | null | "derbydb"
    "jdbc:derby:memory:;databaseName=derbydb"                                                      | "derby:memory"    | "APP"       | null        | null | "derbydb"
    "jdbc:derby:memory:derbydb;databaseName=altdb"                                                 | "derby:memory"    | "APP"       | null        | null | "derbydb"
    "jdbc:derby:memory:derbydb;user=derbyuser;password=pw"                                         | "derby:memory"    | "derbyuser" | null        | null | "derbydb"
    "jdbc:derby://derbyhost:222/memory:derbydb;create=true"                                        | "derby:network"   | "APP"       | "derbyhost" | 222  | "derbydb"
    "jdbc:derby://derbyhost/memory:derbydb;create=true;user=derbyuser;password=pw"                 | "derby:network"   | "derbyuser" | "derbyhost" | 1527 | "derbydb"
    "jdbc:derby://127.0.0.1:1527/memory:derbydb;create=true;user=derbyuser;password=pw"            | "derby:network"   | "derbyuser" | "127.0.0.1" | 1527 | "derbydb"
    "jdbc:derby:directory:derbydb;user=derbyuser;password=pw"                                      | "derby:directory" | "derbyuser" | null        | null | "derbydb"
    "jdbc:derby:classpath:/some/derbydb;user=derbyuser;password=pw"                                | "derby:classpath" | "derbyuser" | null        | null | "/some/derbydb"
    "jdbc:derby:jar:/derbydb;user=derbyuser;password=pw"                                           | "derby:jar"       | "derbyuser" | null        | null | "/derbydb"
    "jdbc:derby:jar:(~/path/to/db.jar)/other/derbydb;user=derbyuser;password=pw"                   | "derby:jar"       | "derbyuser" | null        | null | "(~/path/to/db.jar)/other/derbydb"

    expected = new JDBCMaps.DBInfo(format, null, user, instance, null, host, port)
  }
}
