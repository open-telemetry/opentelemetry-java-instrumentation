/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import java.io.File;
import java.io.IOException;
import org.apache.curator.test.TestingServer;

public final class EmbedZookeeperServer {

  private static TestingServer testingServer;

  public static void start(int port) {
    try {
      testingServer =
          new TestingServer(
              port, new File(String.format("target/test_zk_data/%s/", System.nanoTime())));
    } catch (Exception ex) {
      throw new RuntimeException("Failed to start embedded ZooKeeper server", ex);
    }
  }

  public static void stop() {
    if (testingServer != null) {
      try {
        testingServer.close();
      } catch (IOException ex) {
        throw new RuntimeException("Failed to stop embedded ZooKeeper server", ex);
      }
    }
  }

  private EmbedZookeeperServer() {}
}
