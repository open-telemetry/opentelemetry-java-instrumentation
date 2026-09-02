/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import org.junit.jupiter.api.Test;

class GeodeServerTargetBuilderTest {

  @Test
  void explicitServersArePreferredOverLocatorDiscoveryAndServerGroup() {
    GeodeServerTargetBuilder builder = new GeodeServerTargetBuilder();
    builder.addLocator("locator.example", 10334);
    builder.setServerGroup("orders");
    builder.addServer("cache.example", 40404);

    DbServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidExplicitServerDoesNotFallBackToLocatorDiscovery() {
    GeodeServerTargetBuilder builder = new GeodeServerTargetBuilder();
    builder.addLocator("locator.example", 10334);
    builder.addServer(" ", 40404);

    assertThat(builder.build()).isNull();
  }

  @Test
  void locatorDiscoveryKeepsEverySortedLocatorAndServerGroup() {
    GeodeServerTargetBuilder builder = new GeodeServerTargetBuilder();
    builder.addLocator("z.example", 10334);
    builder.addLocator("e.example", 10334);
    builder.addLocator("d.example", 10334);
    builder.addLocator("c.example", 10334);
    builder.addLocator("b.example", 10334);
    builder.addLocator("a.example", 10334);
    builder.setServerGroup("orders");

    DbServerTarget target = builder.build();
    assertThat(target.getAddress())
        .isEqualTo(
            "a.example:10334,b.example:10334,c.example:10334,d.example:10334,"
                + "e.example:10334,z.example:10334/orders");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void serversAreSortedAndLimitedToFive() {
    GeodeServerTargetBuilder builder = new GeodeServerTargetBuilder();
    builder.addServer("z.example", 40404);
    builder.addServer("e.example", 40404);
    builder.addServer("d.example", 40404);
    builder.addServer("c.example", 40404);
    builder.addServer("b.example", 40404);
    builder.addServer("a.example", 40404);

    DbServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("a.example,b.example,c.example,d.example,e.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void resetForgetsTheConfiguration() {
    GeodeServerTargetBuilder builder = new GeodeServerTargetBuilder();
    builder.addServer("one.example", 40404);
    builder.addLocator("locator.example", 10334);
    builder.setServerGroup("orders");
    builder.reset();

    assertThat(builder.build()).isNull();

    builder.addServer("two.example", 40405);
    assertThat(builder.build().getAddress()).isEqualTo("two.example");
    assertThat(builder.build().getPort()).isEqualTo(40405);
  }

  @Test
  void builtTargetIsNotChangedByLaterConfiguration() {
    GeodeServerTargetBuilder builder = new GeodeServerTargetBuilder();
    builder.addServer("one.example", 40404);

    DbServerTarget target = builder.build();

    builder.addServer("two.example", 40405);
    builder.setServerGroup("orders");

    assertThat(target.getAddress()).isEqualTo("one.example");
    assertThat(target.getPort()).isNull();
    assertThat(builder.build().getAddress()).isEqualTo("one.example:40404,two.example:40405");
  }
}
