/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CouchbaseConnectionStringsTest {

  @ParameterizedTest
  @MethodSource("defaultPortConnectionStrings")
  void defaultPortIsOmitted(String connectionString) {
    CouchbaseServerTarget target = CouchbaseConnectionStrings.target(connectionString);

    assertThat(target.getAddress()).isEqualTo("node");
    assertThat(target.getPort()).isNull();
  }

  private static Stream<Arguments> defaultPortConnectionStrings() {
    return Stream.of(
        argumentSet("implicit couchbase scheme", "node:11210"),
        argumentSet("couchbase scheme", "couchbase://node:11210"),
        argumentSet("couchbases scheme", "couchbases://node:11207"));
  }

  @Test
  void dnsSrvIdentityPreservesSchemeAndIsSanitized() {
    ConnectionString connectionString = ConnectionString.create("couchbases://cluster.example");
    assertThat(connectionString.isValidDnsSrv()).isTrue();

    CouchbaseServerTarget target = CouchbaseConnectionStrings.target(connectionString);
    assertThat(target.getAddress()).isEqualTo("couchbases://cluster.example");
    assertThat(target.getPort()).isNull();

    CouchbaseServerTarget sanitized =
        CouchbaseConnectionStrings.target(
            "couchbases://user@cluster.example/travel-sample?kv_timeout=5s#anchor");
    assertThat(sanitized.getAddress()).isEqualTo("couchbases://cluster.example");
    assertThat(sanitized.getPort()).isNull();
  }

  @Test
  void severalDefaultPortSeedsKeepTheirOrderAndDuplicates() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target("couchbase://two.example,one.example:11210,two.example");

    assertThat(target.getAddress()).isEqualTo("two.example,one.example,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalSeedsWithTheSameNonDefaultPortKeepInlinePorts() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target("couchbase://two.example:11211,one.example:11211");

    assertThat(target.getAddress()).isEqualTo("two.example:11211,one.example:11211");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalSeedsWithDifferentPortsKeepInlinePorts() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target("couchbase://two.example:11211,one.example");

    assertThat(target.getAddress()).isEqualTo("two.example:11211,one.example:11210");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void exactlyFiveSeedsArePreserved() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            "couchbase://five.example,four.example,one.example,three.example,two.example");

    assertThat(target.getAddress())
        .isEqualTo("five.example,four.example,one.example,three.example,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void firstFiveOfSixSeedsArePreserved() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            "couchbase://six.example,five.example,four.example,three.example,two.example,one.example");

    assertThat(target.getAddress())
        .isEqualTo("six.example,five.example,four.example,three.example,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void nonDefaultSixthSeedControlsPortRendering() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            "couchbase://one.example,two.example,three.example,four.example,five.example,six.example:11211");

    assertThat(target.getAddress())
        .isEqualTo(
            "one.example:11210,two.example:11210,three.example:11210,four.example:11210,five.example:11210");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void credentialsParametersBucketsAndFragmentsAreStrippedFromDnsSrvIdentity() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            "couchbase://user@node.example/travel-sample?kv_timeout=5s#anchor");

    assertThat(target.getAddress()).isEqualTo("couchbase://node.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ipv4SeedsAreReportedAsConfigured() {
    CouchbaseServerTarget single = CouchbaseConnectionStrings.target("couchbase://192.0.2.1:11210");
    assertThat(single.getAddress()).isEqualTo("192.0.2.1");
    assertThat(single.getPort()).isNull();
    assertThat(
            CouchbaseConnectionStrings.target("couchbase://192.0.2.1,192.0.2.2:11210").getAddress())
        .isEqualTo("192.0.2.1,192.0.2.2");
  }

  @Test
  void ipv6SeedsAreBracketedOnlyWhenTheyShareAnAddress() {
    CouchbaseServerTarget single =
        CouchbaseConnectionStrings.target("couchbase://[2001:db8::1]:11210");
    assertThat(single.getAddress()).isEqualTo("2001:db8::1");
    assertThat(single.getPort()).isNull();
    assertThat(
            CouchbaseConnectionStrings.target("couchbase://[2001:db8::1]:11211,[2001:db8::2]")
                .getAddress())
        .isEqualTo("[2001:db8::1]:11211,[2001:db8::2]:11210");
  }

  @Test
  void connectionStringTheDriverRejectsHasNoTarget() {
    assertThat(CouchbaseConnectionStrings.target("mysql://node.example")).isNull();
    assertThat(CouchbaseConnectionStrings.target((String) null)).isNull();
    assertThat(CouchbaseConnectionStrings.target("")).isNull();
    assertThat(CouchbaseConnectionStrings.target((ConnectionString) null)).isNull();
    assertThat(CouchbaseConnectionStrings.target("couchbase://")).isNull();
  }
}
