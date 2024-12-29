/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.inetaddress;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

public class InetAddressResolverTest {

  @Test
  void agentStartShouldNotTriggerLoadingCustomInetAddressResolvers() throws Exception {
    // This system property is set in TestResourceProvider
    assertThat(System.getProperty("test.resource.provider.called")).isEqualTo("true");
    // Agent start should not trigger loading (and instantiating) custom InetAddress resolvers
    assertThat(TestAddressResolver.isInstantiated()).isFalse();

    // Trigger loading (and instantiating) custom InetAddress resolvers manually
    InetAddress.getAllByName("test");

    // Verify that custom InetAddress resolver loaded and instantiated
    assertThat(TestAddressResolver.isInstantiated()).isTrue();
  }
}
