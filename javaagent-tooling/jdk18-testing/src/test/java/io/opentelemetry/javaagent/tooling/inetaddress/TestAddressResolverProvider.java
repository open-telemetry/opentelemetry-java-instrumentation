/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.inetaddress;

import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;

public class TestAddressResolverProvider extends InetAddressResolverProvider {

  @Override
  public InetAddressResolver get(Configuration configuration) {
    return new TestAddressResolver();
  }

  @Override
  public String name() {
    return "Test Internet Address Resolver Provider";
  }
}
