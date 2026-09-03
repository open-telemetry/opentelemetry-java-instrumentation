/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DbServerTargetTest {

  private static final int DEFAULT_PORT = 9042;

  @ParameterizedTest
  @MethodSource("unixSocketPaths")
  void unixSocketPreservesAcceptedPathAndHasNoPort(String path) {
    DbServerTarget target = DbServerTarget.unixSocket(path);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(path);
    assertThat(target.getPort()).isNull();
  }

  private static Stream<Arguments> unixSocketPaths() {
    return Stream.of(
        argumentSet("typical path", "/var/run/postgresql/.s.PGSQL.5432"),
        argumentSet("path with spaces", "/var/run/database socket "),
        argumentSet("path with URI punctuation", "/var/run/db:5432;socket"),
        argumentSet("repeated leading slash", "//host/share/database.sock"));
  }

  @ParameterizedTest
  @MethodSource("invalidUnixSocketPaths")
  void unixSocketRejectsInvalidPath(String path) {
    assertThat(DbServerTarget.unixSocket(path)).isNull();
  }

  private static Stream<Arguments> invalidUnixSocketPaths() {
    return Stream.of(
        argumentSet("null", (String) null),
        argumentSet("empty", ""),
        argumentSet("root only", "/"),
        argumentSet("relative", "var/run/database.sock"),
        argumentSet("leading whitespace", " /var/run/database.sock"),
        argumentSet("comma", "/var/run/database,sock"),
        argumentSet("equals", "/var/run/database=sock"),
        argumentSet("percent", "/var/run/database%sock"),
        argumentSet("at sign", "/var/run/database@sock"),
        argumentSet("question mark", "/var/run/database?sock"),
        argumentSet("fragment", "/var/run/database#sock"));
  }

  @Test
  void singleEndpointOnDefaultPortReportsNoPort() {
    DbServerTarget target = builder().addEndpoint("cassandra.example.com", DEFAULT_PORT).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleEndpointWithoutConfiguredPortReportsNoPort() {
    DbServerTarget target = builder().addEndpoint("cassandra.example.com", -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleEndpointReportsNonDefaultPortSeparately() {
    DbServerTarget target = builder().addEndpoint("cassandra.example.com", 19042).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isEqualTo(19042);
  }

  @Test
  void singleIpv6EndpointIsNotBracketedWhenThePortIsSeparate() {
    DbServerTarget target = builder().addEndpoint("2001:db8::1", 19042).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("2001:db8::1");
    assertThat(target.getPort()).isEqualTo(19042);
  }

  @Test
  void endpointSpecificDefaultPortIsHonored() {
    DbServerTarget target =
        DbServerTarget.builder(-1).addEndpoint("es.example.com", -1, 443).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("es.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointSpecificDefaultPortsAreComparedPerEndpoint() {
    DbServerTarget target =
        DbServerTarget.builder(-1)
            .addEndpoint("plain.example.com", -1, 80)
            .addEndpoint("secure.example.com", -1, 443)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("plain.example.com,secure.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointWithoutConfiguredPortAndWithoutDefaultPortDropsTheTarget() {
    assertThat(DbServerTarget.builder(-1).addEndpoint("es.example.com", -1).build()).isNull();
  }

  @Test
  void multipleEndpointsOnDefaultPortsOmitEveryPort() {
    DbServerTarget target =
        builder()
            .addEndpoint("a.example.com", DEFAULT_PORT)
            .addEndpoint("b.example.com", -1)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com,b.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void multipleEndpointsSharingANonDefaultPortCarryEveryPort() {
    DbServerTarget target =
        builder().addEndpoint("a.example.com", 19042).addEndpoint("b.example.com", 19042).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com:19042,b.example.com:19042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void multipleEndpointsWithMixedPortsCarryEveryPortIncludingTheDefaultOne() {
    DbServerTarget target =
        builder()
            .addEndpoint("a.example.com", DEFAULT_PORT)
            .addEndpoint("b.example.com", 19042)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com:9042,b.example.com:19042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ipv6EndpointsAreBracketedOnlyWhenTheyCarryAPort() {
    DbServerTarget withoutPorts =
        builder().addEndpoint("2001:db8::1", -1).addEndpoint("2001:db8::2", -1).build();
    DbServerTarget withPorts =
        builder().addEndpoint("2001:db8::1", 19042).addEndpoint("2001:db8::2", -1).build();

    assertThat(withoutPorts).isNotNull();
    assertThat(withoutPorts.getAddress()).isEqualTo("2001:db8::1,2001:db8::2");
    assertThat(withPorts).isNotNull();
    assertThat(withPorts.getAddress()).isEqualTo("[2001:db8::1]:19042,[2001:db8::2]:9042");
  }

  @Test
  void duplicateEndpointsArePreserved() {
    DbServerTarget target =
        builder().addEndpoint("a.example.com", -1).addEndpoint("a.example.com", -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com,a.example.com");
  }

  @Test
  void endpointsAreSortedByDefault() {
    DbServerTarget target =
        builder()
            .addEndpoint("c.example.com", -1)
            .addEndpoint("a.example.com", -1)
            .addEndpoint("b.example.com", -1)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com,b.example.com,c.example.com");
  }

  @Test
  void nativeOrderIsPreservedWhenSortingIsOff() {
    DbServerTarget target =
        builder()
            .setSorted(false)
            .addEndpoint("c.example.com", -1)
            .addEndpoint("a.example.com", -1)
            .addEndpoint("b.example.com", -1)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("c.example.com,a.example.com,b.example.com");
  }

  @Test
  void endpointListIsCappedAtFiveEndpoints() {
    DbServerTargetBuilder builder = builder();
    for (int i = 1; i <= 7; i++) {
      builder.addEndpoint("node" + i + ".example.com", -1);
    }
    DbServerTarget target = builder.build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo(
            "node1.example.com,node2.example.com,node3.example.com,node4.example.com,"
                + "node5.example.com");
  }

  @Test
  void endpointCapIsConfigurable() {
    DbServerTarget target =
        builder()
            .setMaxEndpoints(2)
            .addEndpoint("a.example.com", -1)
            .addEndpoint("b.example.com", -1)
            .addEndpoint("c.example.com", -1)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com,b.example.com");
  }

  @Test
  void endpointCapMustBePositive() {
    assertThatThrownBy(() -> builder().setMaxEndpoints(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void endpointsAreSortedBeforeTheyAreCapped() {
    DbServerTarget target =
        builder()
            .setMaxEndpoints(2)
            .addEndpoint("c.example.com", -1)
            .addEndpoint("a.example.com", -1)
            .addEndpoint("b.example.com", -1)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com,b.example.com");
  }

  @Test
  void anUnsafeEndpointBeyondTheCapStillDropsTheTarget() {
    DbServerTargetBuilder builder = builder().setMaxEndpoints(1);
    builder.addEndpoint("a.example.com", -1);
    builder.addEndpoint("evil host", -1);

    assertThat(builder.build()).isNull();
  }

  @Test
  void portAlwaysInlineRendersTheDefaultPortOfASingleEndpoint() {
    DbServerTarget target =
        builder().setPortAlwaysInline(true).addEndpoint("locator.example.com", -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("locator.example.com:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void portAlwaysInlineRendersEveryDefaultPortOfSeveralEndpoints() {
    DbServerTarget target =
        builder()
            .setPortAlwaysInline(true)
            .addEndpoint("a.example.com", -1)
            .addEndpoint("2001:db8::1", -1)
            .build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[2001:db8::1]:9042,a.example.com:9042");
  }

  @Test
  void suffixIsAppendedAsAPathSegmentAndForcesInlinePorts() {
    DbServerTarget target =
        builder().setSuffix("group-1").addEndpoint("locator.example.com", 10334).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("locator.example.com:10334/group-1");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "", " ", ".", "..", "a/b", "a,b", "a b", "a?b", "a#b", "a%b", "a\\b", "a@b", "a\nb", "é"
      })
  void unsafeSuffixIsDroppedAndTheTargetIsKept(String suffix) {
    DbServerTarget target = builder().setSuffix(suffix).addEndpoint("a.example.com", -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void nullSuffixIsDropped() {
    DbServerTarget target = builder().setSuffix(null).addEndpoint("a.example.com", -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com");
  }

  @Test
  void suffixIsTrimmed() {
    DbServerTarget target =
        builder().setSuffix("  group-1  ").addEndpoint("a.example.com", -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com:9042/group-1");
  }

  @Test
  void emptyBuilderHasNoTarget() {
    assertThat(builder().build()).isNull();
  }

  @Test
  void oneUnsafeEndpointDropsTheWholeTarget() {
    assertThat(
            builder()
                .addEndpoint("a.example.com", -1)
                .addEndpoint("b.example.com", -1)
                .addEndpoint(null, -1)
                .build())
        .isNull();
  }

  @Test
  void hostIsTrimmed() {
    DbServerTarget target = builder().addEndpoint("  a.example.com  ", -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com");
  }

  @Test
  void bracketsAreStrippedFromAnIpv6Host() {
    DbServerTarget target = builder().addEndpoint("[2001:db8::1]", 19042).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("2001:db8::1");
    assertThat(target.getPort()).isEqualTo(19042);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a.example.com",
        "example.com.",
        "localhost",
        "node-1",
        "node_1.example.com",
        "EXAMPLE.com",
        "12345",
        "1.2.3.4",
        "255.255.255.255",
        "0.0.0.0",
        "2001:db8::1",
        "::1",
        "::",
        "::ffff:192.168.0.1",
        "fe80::1%eth0",
        "FE80::1"
      })
  void safeHostIsAccepted(String host) {
    DbServerTarget target = builder().addEndpoint(host, -1).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(host.trim());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        ".",
        "a..b",
        "-example.com",
        "example-.com",
        "_example.com",
        "example.com-",
        "exa mple.com",
        "example.com/path",
        "example.com?q",
        "example.com#f",
        "user@example.com",
        "a.example.com,b.example.com",
        "example.com:9042",
        "[example.com]",
        "[2001:db8::1",
        "2001:db8::1]",
        "[[2001:db8::1]]",
        "[]",
        "1.2.3.4.5",
        "1.2.3",
        "256.1.1.1",
        "010.1.1.1",
        "1.2.3.-4",
        "::ffff:010.1.1.1",
        "1:2",
        "1:2:3:4:5:6:7:8:9",
        "gg::1",
        "fe80::1%",
        "fe80::1%eth 0",
        "fe80::1%a%b",
        "exa\tmple.com",
        "exa\u0000mple.com",
        "éxample.com",
        "example。com"
      })
  void unsafeHostIsRejected(String host) {
    assertThat(builder().addEndpoint(host, -1).build()).isNull();
  }

  @Test
  void hostNameLengthLimitIsEnforced() {
    String segment = repeated('a', 63);
    String longestValidHost = segment + "." + segment + "." + segment + "." + repeated('a', 61);
    String tooLongHost = longestValidHost + "a";

    assertThat(builder().addEndpoint(longestValidHost, -1).build()).isNotNull();
    assertThat(builder().addEndpoint(tooLongHost, -1).build()).isNull();
  }

  @Test
  void hostNameSegmentLengthLimitIsEnforced() {
    assertThat(builder().addEndpoint(repeated('a', 63) + ".example.com", -1).build()).isNotNull();
    assertThat(builder().addEndpoint(repeated('a', 64) + ".example.com", -1).build()).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 65536, Integer.MAX_VALUE})
  void invalidDefaultPortDropsTheTarget(int defaultPort) {
    assertThat(DbServerTarget.builder(defaultPort).addEndpoint("a.example.com", -1).build())
        .isNull();
  }

  @Test
  void portZeroDropsTheTarget() {
    assertThat(builder().addEndpoint("a.example.com", 0).build()).isNull();
  }

  @Test
  void portAboveTheValidRangeDropsTheTarget() {
    assertThat(builder().addEndpoint("a.example.com", 65536).build()).isNull();
  }

  @Test
  void highestValidPortIsAccepted() {
    DbServerTarget target = builder().addEndpoint("a.example.com", 65535).build();

    assertThat(target).isNotNull();
    assertThat(target.getPort()).isEqualTo(65535);
  }

  @Test
  void socketAddressIsAddedWithoutResolvingIt() {
    DbServerTarget target =
        builder().addEndpoint(InetSocketAddress.createUnresolved("a.example.com", 19042)).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example.com");
    assertThat(target.getPort()).isEqualTo(19042);
  }

  @Test
  void numericSocketAddressKeepsItsLiteralForm() throws UnknownHostException {
    InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    DbServerTarget target =
        builder().addEndpoint(new InetSocketAddress(loopback, DEFAULT_PORT)).build();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("127.0.0.1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void nullSocketAddressDropsTheTarget() {
    assertThat(builder().addEndpoint((InetSocketAddress) null).build()).isNull();
  }

  @Test
  void buildCanBeCalledRepeatedly() {
    DbServerTargetBuilder builder =
        builder().addEndpoint("b.example.com", -1).addEndpoint("a.example.com", -1);

    DbServerTarget first = builder.build();
    DbServerTarget second = builder.build();

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
  }

  private static DbServerTargetBuilder builder() {
    return DbServerTarget.builder(DEFAULT_PORT);
  }

  private static String repeated(char c, int count) {
    StringBuilder value = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      value.append(c);
    }
    return value.toString();
  }
}
