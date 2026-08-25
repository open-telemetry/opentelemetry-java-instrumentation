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
  void severalServersDoNotFormOneAddress() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("two.example", 40405);
    builder.addServer("one.example", 40404);

    assertThat(builder.build()).isNull();
  }

  @Test
  void theSameServerAddedTwiceIsStillOneServer() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("cache.example", 40404);
    builder.addServer("cache.example", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isEqualTo(40404);
  }

  @Test
  void poolWithoutServersOrLocatorsHasNoTarget() {
    assertThat(GeodeServerTarget.builder().build()).isNull();
  }

  @Test
  void locatorIsNamedWithoutItsPort() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("locator.example", 10334);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("locator.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalLocatorsDoNotFormOneAddress() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("two.example", 10335);
    builder.addLocator("one.example", 10334);

    assertThat(builder.build()).isNull();
  }

  @Test
  void theSameLocatorAddedTwiceIsStillOneLocator() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("locator.example", 10334);
    builder.addLocator("locator.example", 10334);

    assertThat(builder.build().getAddress()).isEqualTo("locator.example");
  }

  @Test
  void explicitServerIsPreferredOverLocatorDiscovery() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("locator.example", 10334);
    builder.addServer("cache.example", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isEqualTo(40404);
  }

  @Test
  void severalServersHideTheLocatorsAsWell() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("locator.example", 10334);
    builder.addServer("one.example", 40404);
    builder.addServer("two.example", 40405);

    assertThat(builder.build()).isNull();
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
  void locatorThatCannotBeNamedDropsTheLocatorList() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("one.example", 10334);
    builder.addLocator("  ", 10335);

    assertThat(builder.build()).isNull();
  }

  @Test
  void ipv6ServersKeepTheirAddress() {
    GeodeServerTarget.Builder bare = GeodeServerTarget.builder();
    bare.addServer("2001:db8::1", 40404);

    assertThat(bare.build().getAddress()).isEqualTo("2001:db8::1");
    assertThat(bare.build().getPort()).isEqualTo(40404);

    GeodeServerTarget.Builder bracketed = GeodeServerTarget.builder();
    bracketed.addServer("[2001:db8::1]", 40404);

    assertThat(bracketed.build().getAddress()).isEqualTo("2001:db8::1");
    assertThat(bracketed.build().getPort()).isEqualTo(40404);
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
    builder.addLocator("locator.example", 10334);
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

    assertThat(target.getAddress()).isEqualTo("one.example");
    assertThat(target.getPort()).isEqualTo(40404);
    assertThat(builder.build()).isNull();
  }
}
