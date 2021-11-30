/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import org.testcontainers.containers.ContainerState;

public final class TestContainersUtils {

  public static boolean isContainerIpAddress(ContainerState container, Object ip) {
    String containerIp = container.getContainerIpAddress();
    // getContainerIpAddress() can return "localhost", which obviously is not an IP address
    if (containerIp.equals("localhost")) {
      return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1");
    }
    return containerIp.equals(ip);
  }

  private TestContainersUtils() {}
}
