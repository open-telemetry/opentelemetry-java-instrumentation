/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;

public class RocketMqProxyContainer {
  // TODO(aaron-ai): replace it by the official image.
  private static final String IMAGE_NAME = "aaronai/rocketmq-proxy-it:v1.0.0";

  private final GenericContainer<?> container;
  final String endpoints;

  // We still need this container type to do fixed-port-mapping.
  @SuppressWarnings({"resource", "deprecation", "rawtypes"})
  RocketMqProxyContainer() {
    int proxyPort = PortUtils.findOpenPorts(4);
    int brokerPort = proxyPort + 1;
    int brokerHaPort = proxyPort + 2;
    int namesrvPort = proxyPort + 3;
    endpoints = "127.0.0.1:" + proxyPort;
    container =
        new FixedHostPortGenericContainer(IMAGE_NAME)
            .withFixedExposedPort(proxyPort, proxyPort)
            .withEnv("rocketmq.broker.port", String.valueOf(brokerPort))
            .withEnv("rocketmq.proxy.port", String.valueOf(proxyPort))
            .withEnv("rocketmq.broker.ha.port", String.valueOf(brokerHaPort))
            .withEnv("rocketmq.namesrv.port", String.valueOf(namesrvPort))
            .withExposedPorts(proxyPort);
  }

  void start() {
    container.start();
  }

  void close() {
    container.close();
  }
}
