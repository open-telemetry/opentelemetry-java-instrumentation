/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import org.junit.jupiter.api.Test;

class CouchbaseConnectionStringsTest {

  @Test
  void loneSeedKeepsItsHostAndPort() {
    CouchbaseServerTarget target = CouchbaseConnectionStrings.target("couchbase://node:11210");

    assertThat(target.getAddress()).isEqualTo("node");
    assertThat(target.getPort()).isEqualTo(11210);
  }

  @Test
  void hostThatResolvesThroughDnsSrvIsNamedByItself() {
    ConnectionString connectionString = ConnectionString.create("couchbases://cluster.example");
    assertThat(connectionString.isValidDnsSrv()).isTrue();

    CouchbaseServerTarget target = CouchbaseConnectionStrings.target(connectionString);
    assertThat(target.getAddress()).isEqualTo("cluster.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalSeedsKeepTheirOrderAndDuplicatesAndLoseThePort() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target("couchbase://two.example,one.example:11210,two.example");

    assertThat(target.getAddress()).isEqualTo("two.example,one.example:11210,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void theSchemeIsKeptForSeveralSeeds() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target("couchbases://one.example,two.example");

    assertThat(target.getAddress()).isEqualTo("one.example,two.example");
  }

  @Test
  void credentialsParametersAndBucketsAreStripped() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            "couchbase://user@node.example/travel-sample?kv_timeout=5s");

    assertThat(target.getAddress()).isEqualTo("node.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ipv4SeedsAreReportedAsConfigured() {
    assertThat(CouchbaseConnectionStrings.target("couchbase://192.0.2.1:11210").getAddress())
        .isEqualTo("192.0.2.1");
    assertThat(
            CouchbaseConnectionStrings.target("couchbase://192.0.2.1,192.0.2.2:11210").getAddress())
        .isEqualTo("192.0.2.1,192.0.2.2:11210");
  }

  @Test
  void ipv6SeedsAreBracketedOnlyWhenTheyShareAnAddress() {
    CouchbaseServerTarget single =
        CouchbaseConnectionStrings.target("couchbase://[2001:db8::1]:11210");
    assertThat(single.getAddress()).isEqualTo("2001:db8::1");
    assertThat(single.getPort()).isEqualTo(11210);
    assertThat(
            CouchbaseConnectionStrings.target("couchbase://[2001:db8::1]:11210,[2001:db8::2]")
                .getAddress())
        .isEqualTo("[2001:db8::1]:11210,[2001:db8::2]");
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
