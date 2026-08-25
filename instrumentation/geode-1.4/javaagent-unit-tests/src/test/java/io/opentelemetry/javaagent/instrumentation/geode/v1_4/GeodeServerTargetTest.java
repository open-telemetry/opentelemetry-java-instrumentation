/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeodeServerTargetTest {

  @Test
  void singleServerKeepsItsHostAndPort() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("cache.example", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isEqualTo(40404);
  }

  @Test
  void severalServersAreRenderedAsAList() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);
    builder.addServer("two.example", 40405);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("one.example:40404,two.example:40405");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void serverGroupIsPreferredOverTheServersReachingIt() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("cache.example", 40404);
    builder.setServerGroup("orders");

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("orders");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void serverGroupIsTrimmed() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.setServerGroup("  orders  ");

    assertThat(builder.build().getAddress()).isEqualTo("orders");
  }

  @Test
  void blankServerGroupNamesNothing() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.setServerGroup("   ");
    builder.addServer("cache.example", 40404);

    assertThat(builder.build().getAddress()).isEqualTo("cache.example");
  }

  @Test
  void poolWithoutServersOrAGroupHasNoTarget() {
    assertThat(GeodeServerTarget.builder().build()).isNull();
  }

  @Test
  void serverThatCannotBeNamedDropsTheServerList() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);
    builder.addServer("  ", 40405);

    assertThat(builder.build()).isNull();

    GeodeServerTarget.Builder outOfRange = GeodeServerTarget.builder();
    outOfRange.addServer("one.example", 0);

    assertThat(outOfRange.build()).isNull();
  }

  @Test
  void ipv6ServersKeepTheirAddress() {
    GeodeServerTarget.Builder single = GeodeServerTarget.builder();
    single.addServer("2001:db8::1", 40404);

    assertThat(single.build().getAddress()).isEqualTo("2001:db8::1");
    assertThat(single.build().getPort()).isEqualTo(40404);

    GeodeServerTarget.Builder several = GeodeServerTarget.builder();
    several.addServer("[2001:db8::1]", 40404);
    several.addServer("two.example", 40405);

    assertThat(several.build().getAddress()).isEqualTo("[2001:db8::1]:40404,two.example:40405");
    assertThat(several.build().getPort()).isNull();
  }

  @Test
  void hostsAreCleaned() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("  cache.example  ", 40404);

    assertThat(builder.build().getAddress()).isEqualTo("cache.example");
  }

  @Test
  void resetForgetsTheConfiguration() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);
    builder.setServerGroup("orders");
    builder.reset();

    assertThat(builder.build()).isNull();

    builder.addServer("two.example", 40405);
    assertThat(builder.build().getAddress()).isEqualTo("two.example");
  }

  @Test
  void builtTargetIsNotChangedByLaterConfiguration() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);

    GeodeServerTarget target = builder.build();

    builder.addServer("two.example", 40405);
    builder.setServerGroup("orders");

    assertThat(target.getAddress()).isEqualTo("one.example");
    assertThat(target.getPort()).isEqualTo(40404);
    assertThat(builder.build().getAddress()).isEqualTo("orders");
  }
}
