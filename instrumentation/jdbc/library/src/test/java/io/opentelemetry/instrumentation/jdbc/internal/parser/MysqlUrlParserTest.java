/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.stripIpv6Brackets;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.mysql.cj.conf.ConnectionUrl;
import com.mysql.cj.conf.HostInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.HostPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MysqlUrlParserTest {

  @ParameterizedTest
  @CsvSource(
      value = {
        "h1|h1|",
        "h1:1111|h1|1111",
        "%48%31:1111|h1|1111",
        "H%31:1111|h1|1111",
        "h1:3306|h1|3306",
        "[::1]:1111|::1|1111",
        "::1|::1|",
        "user:example@h1:1111|h1|1111",
        "address=example@h1:1111|h1|1111",
        "us(er):ex(ample)@h1:1111|h1|1111",
        "(user):example@h1:1111|h1|1111",
        "(user):ex(ample@h1:1111|h1|1111",
        "address=(user):example@h1:1111|h1|1111",
        "address=ex(ample@h1:1111|h1|1111",
        "user:ex(ample@h1:1111|h1|1111",
        "address=(host=h1)|h1|",
        "address=(host=h1)(port=1111)|h1|1111",
        "address=(host=%48%31)(port=1111)|h1|1111",
        "address=(host=H%31)(port=1111)|h1|1111",
        "address=(HOST= h1 )(port= 1111 )(user=other)(password=example%40value)|h1|1111",
        "address=(host=::1)(port=1111)|::1|1111",
        "address=(host=h1)(port=1111)(user=other%40example)|h1|1111",
        "(host=h1)|h1|",
        "(host=h1,port=1111)|h1|1111",
        "(host=%48%31,port=1111)|h1|1111",
        "(host=H%31,port=1111)|h1|1111",
        "(port=1111,host=h1)|h1|1111",
        "\" ( host = h1 , port = 1111 ) \"|h1|1111",
        "\"(\t host \r =h1,port=1111)\"|h1|1111",
        "(,,host=h1,=,,)|h1|",
        "(host=h1,port=)|h1|",
        "(host=h1,port)|h1|",
        "(%48ost=h%31,port=%31%31%31%31)|h1|1111",
        "(ho%C5%BFt=h1,port=1111)|h1|1111",
        "(host=::1,port=1111)|::1|1111",
        "(host=%5B::1%5D,port=1111)|::1|1111",
        "(host=h1,user=other,password=example%40value,other=a(b)c,flag)|h1|"
      },
      delimiter = '|',
      quoteCharacter = '"')
  void parsedHostAndOptionalPort(String authority, String host, Integer port) {
    assertThat(MysqlHostParser.parseHostList(authority, true))
        .isNotNull()
        .satisfies(
            hosts ->
                assertThat(hosts.hosts())
                    .extracting(HostPort::host, HostPort::port)
                    .containsExactly(tuple(host, port)));
  }

  @Test
  void emptyAuthorityHasNoConfiguredHosts() {
    assertThat(MysqlHostParser.parseHostList("", true))
        .isNotNull()
        .satisfies(hosts -> assertThat(hosts.hosts()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "h1:1111",
        "us(er):ex(ample)@h1:1111",
        "(user):example@h1:1111",
        "(user):ex(ample@h1:1111",
        "address=(user):example@h1:1111",
        "address=ex(ample@h1:1111",
        "user:ex(ample@h1:1111",
        "address=(host=h1)(port=1111)",
        "address=(host=h1)(port=1111)(user=other@example)"
      })
  void mariaDbUsesTheSameOrdinaryAndAddressHostParsing(String authority) {
    assertThat(MysqlHostParser.parseHostList(authority, false))
        .isNotNull()
        .satisfies(
            hosts ->
                assertThat(hosts.hosts())
                    .extracting(HostPort::host, HostPort::port)
                    .containsExactly(tuple("h1", 1111)));
  }

  @ParameterizedTest
  @MethodSource("connectorUrls")
  void configuredEndpointsMatchConnectorJ(String url) {
    List<HostInfo> expected =
        ConnectionUrl.getConnectionUrlInstance(url, new Properties()).getHostsList();
    int start = url.indexOf("://") + 3;
    int end = UrlParsingUtils.indexOfAny(url, start, '/', '?', '#');
    String authority = end < 0 ? url.substring(start) : url.substring(start, end);

    assertThat(MysqlHostParser.parseHostList(authority, true))
        .isNotNull()
        .satisfies(
            hosts ->
                assertThat(hosts.hosts())
                    .extracting(
                        host -> tuple(host.host(), host.port() == null ? 3306 : host.port()))
                    .containsExactlyElementsOf(
                        expected.stream()
                            .map(host -> tuple(normalizeHost(host.getHost()), host.getPort()))
                            .collect(toList())));

    DbServerTargetBuilder target = DbServerTarget.builder(3306);
    for (HostInfo host : expected) {
      target.addEndpoint(normalizeHost(host.getHost()), host.getPort());
    }
    assertThat(JdbcConnectionUrlParser.parse(url, null).getConfiguredServerTarget())
        .isEqualTo(target.build());
  }

  private static Stream<Arguments> connectorUrls() {
    List<String> first =
        asList(
            "h1:1111",
            "address=(HOST=h1)(port=1111)(user=other%40example)",
            "(host=h%31,port=%31%31%31%31,other=value%40example)");
    List<String> second = asList("h2", "address=(host=h2)", "(%68ost=h2,port=,other=p(a)ss)");
    List<String> third =
        asList(
            "[2001:db8::1]:3333",
            "address=(host=[2001:db8::1])(port=3333)",
            "(host=%5B2001:db8::1%5D,port=3333)");
    List<String> suffixes =
        asList(
            "/db",
            "/db?sessionVariables=email='user@example.com',sql_mode=ANSI",
            "?sessionVariables=email='user@example.com',sql_mode=ANSI");

    List<String> authorities = new ArrayList<>();
    for (String h1 : first) {
      for (String h2 : second) {
        for (String h3 : third) {
          authorities.addAll(withUserInfo(h1, h2, h3));
        }
      }
    }
    List<String> generated = new ArrayList<>();
    for (String prefix :
        asList("jdbc:mysql://", "jdbc:mysql:loadbalance://", "jdbc:mysql:replication://")) {
      for (String authority : authorities) {
        for (String suffix : suffixes) {
          generated.add(prefix + authority + suffix);
        }
      }
    }

    return Stream.concat(
            generated.stream(),
            Stream.of(
                "jdbc:mysql://h%31:%31%31%31%31/db",
                "jdbc:mysql://%48%31:1111/db",
                "jdbc:mysql://address=(host=%48%31)(port=1111)/db",
                "jdbc:mysql://(host=%48%31,port=1111)/db",
                "jdbc:mysql://address=(%68ost=h%31)(%70ort=%31%31%31%31)/db",
                "jdbc:mysql://(user:example@h1:1111/db",
                "jdbc:mysql://address=(user:example@h1:1111/db",
                "jdbc:mysql://user:example@h1:1111,(other):example@h2:2222/db",
                "jdbc:mysql://(host=h1,other=(value),port=1111)/db",
                "jdbc:mysql://h1,h2,h3,h4,h5,(host=h6,port=1111)/db",
                "jdbc:mysql://h1:1111,h1:1111,(host=h1,port=1111)/db",
                "jdbc:mysql://h1:1111,(host=h2,port=2222),address=(host=::1)(user=other%40example),h1:1111/db",
                "jdbc:mysql://(host=h1),address=(host=h2)(port=2222)/db"))
        .map(url -> argumentSet(url, url));
  }

  private static List<String> withUserInfo(String... hosts) {
    List<String> authorities = new ArrayList<>();
    authorities.add(String.join(",", hosts));
    for (int i = 0; i < hosts.length; i++) {
      if (hosts[i].startsWith("(")) {
        continue;
      }
      for (String userInfo :
          asList("user:example@", "(user):ex(ample)@", "user%40example:p%40ss@")) {
        String[] entries = hosts.clone();
        entries[i] = userInfo + entries[i];
        authorities.add(String.join(",", entries));
      }
    }
    return authorities;
  }

  private static String normalizeHost(String host) {
    return stripIpv6Brackets(host).toLowerCase(Locale.ROOT);
  }

  @ParameterizedTest
  @MethodSource("ambiguousCredentialUrls")
  void validHostsCannotRecoverAnAmbiguousCredentialBoundary(String url) {
    DbInfo info = JdbcConnectionUrlParser.parse(url, null);

    assertThat(info.getConfiguredServerTarget()).isNull();
    assertThat(info.getDbNamespace()).isNull();
  }

  private static Stream<Arguments> ambiguousCredentialUrls() {
    List<String> urls = new ArrayList<>();
    for (String prefix :
        asList(
            "jdbc:mysql://",
            "jdbc:mysql:replication://",
            "jdbc:mariadb://",
            "jdbc:mariadb:sequential://")) {
      List<String> hostForms = new ArrayList<>(asList("h1:3306", "address=(host=h1)"));
      if (prefix.startsWith("jdbc:mysql:")) {
        hostForms.add("(host=h1)");
      }
      for (String host : hostForms) {
        for (int hostCount : asList(0, 1, 6)) {
          for (int credentialIndex = 0; credentialIndex <= hostCount; credentialIndex++) {
            List<String> entries = new ArrayList<>();
            for (int i = 0; i < hostCount; i++) {
              entries.add(host);
            }
            entries.add(credentialIndex, "user:123");
            for (String suffix :
                asList(
                    "/a@h2:3306,h3:3306/db",
                    "/a@h2:3306,h3:3306/db?user=admin@corp.com",
                    "?x=y@h2:3306,h3:3306/db",
                    "?x=y@h2:3306,h3:3306",
                    "?x=y@h2:3306,h3:3306&user=admin@corp.com",
                    "?x=/a@h2:3306,h3:3306",
                    "?x=y@address=(host=h2)/db",
                    "?x=y@(host=h2,port=3306)",
                    "?missing@h2:3306",
                    "#ignored?x=y@h2:3306,h3:3306/db",
                    "/db?user=admin@corp.com#ignored@h2")) {
              urls.add(prefix + String.join(",", entries) + suffix);
            }
          }
        }
      }
    }
    return urls.stream().distinct().map(url -> argumentSet(url, url));
  }

  @Test
  void rejectedCredentialBoundariesCannotUseQueryOrDataSourceFallbacks() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "fallback");
    properties.setProperty("portNumber", "1111");
    String url =
        "jdbc:mysql://(host=h1),user:123/a@h2:3306,h3:3306/db"
            + "?serverName=query-host&portNumber=2222";

    assertThat(JdbcConnectionUrlParser.parse(url, properties).getConfiguredServerTarget()).isNull();
  }

  @ParameterizedTest
  @MethodSource("unambiguousQueryUrls")
  void queryValuesDoNotDependOnUnrelatedHostSyntax(String url, boolean multipleHosts) {
    DbInfo info = JdbcConnectionUrlParser.parse(url, null);

    assertThat(info.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create(multipleHosts ? "h1,h2" : "h1", null));
    assertThat(info.getDbNamespace()).isEqualTo(url.contains("/db") ? "db" : null);
  }

  private static Stream<Arguments> unambiguousQueryUrls() {
    List<Arguments> urls = new ArrayList<>();
    for (String prefix :
        asList(
            "jdbc:mysql://",
            "jdbc:mysql:replication://",
            "jdbc:mariadb://",
            "jdbc:mariadb:sequential://")) {
      List<String> hostForms = new ArrayList<>(asList("h1:3306", "address=(host=h1)"));
      if (prefix.startsWith("jdbc:mysql:")) {
        hostForms.add("(host=h1)");
      }
      for (String host : hostForms) {
        for (boolean multipleHosts : asList(false, true)) {
          String authority =
              host + (multipleHosts ? ",user%40example:p%2Fa%3Fs%23s%40word@h2:3306" : "");
          for (String path : asList("", "/db")) {
            for (String query :
                asList(
                    "?user=admin@corp.com",
                    "?sessionVariables=email='user@example.com',sql_mode=ANSI",
                    "?first=user@example.com&second=other@example.org",
                    "?user=user%40example.com&options=(value)",
                    "?user=admin@corp.com&serverSslCert=/etc/ssl/ca.pem",
                    "?user=admin@corp.com#ignored")) {
              String url = prefix + authority + path + query;
              urls.add(argumentSet(url, url, multipleHosts));
            }
          }
        }
      }
    }
    return urls.stream();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:mysql://(user):ex(ample@h1:1111,h2:2222/db",
        "jdbc:mysql://user:ex(ample@h1:1111,h2:2222/db"
      })
  void unbalancedCredentialMarkersDoNotInventHostBoundaries(String url) {
    assertThat(ConnectionUrl.getConnectionUrlInstance(url, new Properties()).getHostsList())
        .extracting(HostInfo::getHost, HostInfo::getPort)
        .containsExactly(tuple("h1:1111,h2", 2222));
    assertThat(JdbcConnectionUrlParser.parse(url, null).getConfiguredServerTarget()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:mysql://h1:1111,(user):example@h2:2222,h3:3333/db",
        "jdbc:mysql://h1:1111,h2:2222,(user):example@h3:3333/db",
        "jdbc:mariadb://h1:1111,(user):example@h2:2222,h3:3333/db",
        "jdbc:mariadb:replication://h1:1111,h2:2222,(user):example@h3:3333/db"
      })
  void laterCredentialsDoNotDiscardPrecedingHosts(String url) {
    DbInfo info = JdbcConnectionUrlParser.parse(url, null);

    assertThat(info.getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("h1:1111,h2:2222,h3:3333", null));
    assertThat(info.getDbNamespace()).isEqualTo("db");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "h1:1111,(host=h2,port=65536)",
        "h1:1111,(Host=h2,host=h3)",
        "h1:1111,(host=h2,%68ost=h3)",
        "h1:1111,(host=h2,port=2222,Port=3333)",
        "h1:1111,user:example@other@h2:2222",
        "h1:1111,(host=h2,other=user@example)",
        "h1:1111,address=(host=h2)(user=other@example)",
        "h1:1111,address=(ignored=(host=h2))(port=2222)",
        "h1:1111,address=(host=h2)unconsumed",
        "h1:1111,(host=h2)unconsumed",
        "h1:1111,(host=h2,other=bad%encoding)",
        "h1:1111,[(host=h2),(host=h3)]",
        "h1,h2,h3,h4,h5,(host=h6,port=65536)",
        "h1,h2,h3,h4,h5,user:example@other@h6",
        " ",
        "h1,",
        "h1:invalid",
        "h1:",
        "h1:0",
        "h1:65536",
        "[::1]:invalid",
        "[::1]extra",
        "[h1]",
        "not a host",
        "unexpected=value",
        "address=(port=1111)",
        "address=(host=h1)(host=h2)",
        "address=(host=h1)(port=1111)(port=2222)",
        "address=(host=h1)(port=65536)",
        "address=(host=h1",
        "(host=h1",
        "(host=h1))",
        "(host=h1)(port=1111)",
        "(host=(h1),port=1111)",
        "(host=h1,(port)=1111)",
        "(port=1111)",
        "(host=)",
        "(host)",
        "(host=h1,host=h2)",
        "(host=h1,%68ost=h2)",
        "(host=h1,ho%C5%BFt=h2)",
        "(Host=h1,host=h2)",
        "(host=h1,port=1111,port)",
        "(host=h1,port=65536)",
        "(host=h1,port=-1)",
        "(host=h1,port=+1111)",
        "(host=h1,port=not-a-port)",
        "(host=h1,=value)",
        "(host=h1,$key=value)",
        "(host=h1,\u2003key=value)",
        "(host=h1,other=bad%encoding)",
        "(%host=h1)",
        "(host=h1%20)",
        "(host=%09h1)",
        "(host=h1%29%28port%3d1111)",
        "(host=fe80::1%25eth0)",
        "(host=h1,other=value@h2)",
        "(host=h1,other=value@h2",
        "h1,address=(host=h2)(user=other@h3",
        "h1,(host=h2,other=value@h3",
        "(host=h1,other=value#h2)",
        "user:example@(host=h1)",
        "user:ex(ample)@(host=h1)",
        "[(host=h1),(host=h2)]",
        "(host=h1),address=(port=1111)"
      })
  void unsafeEntriesInvalidateTheWholeTargetWithoutDiscardingTheDatabase(String authority) {
    assertThat(MysqlHostParser.parseHostList(authority, true)).isNull();
    DbInfo info = JdbcConnectionUrlParser.parse("jdbc:mysql://" + authority + "/db", null);

    assertThat(info.getConfiguredServerTarget()).isNull();
    assertThat(info.getDbNamespace()).isEqualTo(authority.indexOf('#') < 0 ? "db" : null);
  }

  @Test
  void mariaDbRejectsKeyValueHostsEvenInMixedLists() {
    assertThat(MysqlHostParser.parseHostList("h1:1111,(host=h2,port=2222)", false)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "JDBC:MYSQL://H1:1111/DB%41",
        "JDBC:MYSQL://us\u0130r:example@H1:1111/DB%41", // U+0130 is Turkish capital I with dot.
        "JDBC:MYSQL://%48%31:1111/DB%41",
        "JDBC:MYSQL://H%31:1111/DB%41",
        "JDBC:MYSQL://(HOST=%48%31,PORT=1111)/DB%41",
        "JDBC:MYSQL://(HOST=H%31,PORT=1111)/DB%41",
        "JDBC:MYSQL://address=(HOST=%48%31)(PORT=1111)/DB%41",
        "JDBC:MYSQL://address=(HOST=H%31)(PORT=1111)/DB%41",
        "JDBC:TRACING:MYSQL://(HOST=H1,PORT=1111)/DB%41",
        "JDBC:AWS-WRAPPER:MYSQL://address=(HOST=H1)(PORT=1111)/DB%41"
      })
  void namespaceAndHostNormalizationRemainCompatible(String url) {
    DbInfo info = JdbcConnectionUrlParser.parse(url, null);

    assertThat(info.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("h1", 1111));
    assertThat(info.getDbNamespace()).isEqualTo("dbA");
  }

  @ParameterizedTest
  @ValueSource(strings = {"h1:1111", "(host=h1,port=1111)", "address=(host=h1)(port=1111)"})
  void malformedDatabasePathsDoNotInvalidateConfiguredHosts(String authority) {
    DbInfo info = JdbcConnectionUrlParser.parse("jdbc:mysql://" + authority + "/db name", null);

    assertThat(info.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("h1", 1111));
    assertThat(info.getDbNamespace()).isNull();
  }
}
