/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HbaseMasterTargetTest {

  @Test
  void sortsEndpointsAndPreservesDuplicates() {
    Configuration firstConfiguration = new Configuration(false);
    firstConfiguration.set("hbase.masters", "master-b:16001,master-a,master-b:16001,master-c");
    firstConfiguration.set("hbase.master.port", "17000");

    Configuration secondConfiguration = new Configuration(false);
    secondConfiguration.set("hbase.masters", "master-c,master-b:16001,master-a,master-b:16001");
    secondConfiguration.set("hbase.master.port", "17000");

    assertThat(HbaseMasterTarget.from(firstConfiguration, true))
        .isEqualTo("master-a:17000,master-b:16001,master-b:16001,master-c:17000");
    assertThat(HbaseMasterTarget.from(secondConfiguration, true))
        .isEqualTo("master-a:17000,master-b:16001,master-b:16001,master-c:17000");
    assertThat(HbaseMasterTarget.from(firstConfiguration, false))
        .isEqualTo("master-a:16000,master-b:16001,master-b:16001,master-c:16000");
  }

  @Test
  void capsCanonicalEndpointsAtFive() {
    Configuration configuration = new Configuration(false);
    configuration.set(
        "hbase.masters", "master-f,master-c,master-a,master-b,master-c,master-e,master-d");
    configuration.set("hbase.master.port", "17000");

    assertThat(HbaseMasterTarget.from(configuration, true))
        .isEqualTo("master-a:17000,master-b:17000,master-c:17000,master-c:17000,master-d:17000");
  }

  @Test
  void usesDefaultPortWhenConfiguredPortIsZero() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.masters", "master-a");
    configuration.set("hbase.master.port", "0");

    assertThat(HbaseMasterTarget.from(configuration, true)).isEqualTo("master-a:16000");
  }

  @Test
  void rendersConfiguredHostname() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.master.hostname", "master.test");
    configuration.setInt("hbase.master.port", 17000);

    assertThat(HbaseMasterTarget.from(configuration, true)).isEqualTo("master.test:17000");

    configuration.set("hbase.masters", "");
    assertThat(HbaseMasterTarget.from(configuration, true)).isEqualTo("master.test:17000");
  }

  @Test
  void rendersIpv6Endpoints() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.masters", "2001:db8::2,[2001:db8::1]:16001,[2001:db8::3]");

    assertThat(HbaseMasterTarget.from(configuration, true))
        .isEqualTo("[2001:db8::1]:16001,[2001:db8::2]:16000,[2001:db8::3]:16000");
  }

  @Test
  void omitsImplicitDnsTarget() {
    assertThat(HbaseMasterTarget.from(new Configuration(false), true)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"master-a,,master-b", "user@master-a", "master-a/path"})
  void rejectsInvalidOrUnsafeMasters(String masters) {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.masters", masters);

    assertThat(HbaseMasterTarget.from(configuration, true)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "65536", "not-a-port"})
  void rejectsInvalidDefaultPort(String masterPort) {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.masters", "master-a");
    configuration.set("hbase.master.port", masterPort);

    assertThat(HbaseMasterTarget.from(configuration, true)).isNull();
  }
}
