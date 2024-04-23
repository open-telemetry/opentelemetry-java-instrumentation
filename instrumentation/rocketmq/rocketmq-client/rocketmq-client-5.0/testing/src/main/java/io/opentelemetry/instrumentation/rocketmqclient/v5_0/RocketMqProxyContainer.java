/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import java.net.UnknownHostException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;

public class RocketMqProxyContainer {
  // TODO(aaron-ai): replace it by the official image.
  private static final String IMAGE_NAME = "zackman0010/rocketmq-proxy-it:v1.0.2";

  private final GenericContainer<?> container;
  final String endpoints;

  // We still need this container type to do fixed-port-mapping.
  @SuppressWarnings({"resource", "deprecation", "rawtypes"})
  RocketMqProxyContainer() throws UnknownHostException {
    int proxyPort = PortUtils.findOpenPorts(4);
    int brokerPort = proxyPort + 1;
    int brokerHaPort = proxyPort + 2;
    int namesrvPort = proxyPort + 3;
    // Although this function says "IpAddress" in the name, it actually returns a hostname.
    String dockerHost = DockerClientFactory.instance().dockerHostIpAddress();
    String ip;
    ip = java.net.InetAddress.getByName(dockerHost).getHostAddress();
    container =
        new FixedHostPortGenericContainer(IMAGE_NAME)
            .withFixedExposedPort(proxyPort, proxyPort)
            .withFixedExposedPort(brokerPort, brokerPort)
            .withEnv("rocketmq.broker.port", String.valueOf(brokerPort))
            .withEnv("rocketmq.proxy.port", String.valueOf(proxyPort))
            .withEnv("rocketmq.broker.ha.port", String.valueOf(brokerHaPort))
            .withEnv("rocketmq.namesrv.port", String.valueOf(namesrvPort))
            .withEnv("rocketmq.proxy.ip", ip)
            .withExposedPorts(proxyPort, brokerPort);
    endpoints = ip + ":" + proxyPort;
  }

  void start() {
    container.start();
  }

  void close() {
    container.close();
  }
}
