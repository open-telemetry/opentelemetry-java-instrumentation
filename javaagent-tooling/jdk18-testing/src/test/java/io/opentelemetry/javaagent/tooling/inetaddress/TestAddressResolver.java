/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.inetaddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.util.stream.Stream;

public class TestAddressResolver implements InetAddressResolver {

  private static volatile boolean instantiated = false;

  @SuppressWarnings("StaticAssignmentInConstructor")
  public TestAddressResolver() {
    TestAddressResolver.instantiated = true;
  }

  public static boolean isInstantiated() {
    return instantiated;
  }

  @Override
  public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
      throws UnknownHostException {
    if (host.equals("test")) {
      return Stream.of(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
    }
    throw new UnknownHostException();
  }

  @Override
  public String lookupByAddress(byte[] addr) {
    throw new UnsupportedOperationException();
  }
}
