/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import java.io.File;
import org.apache.curator.test.TestingServer;

public final class EmbedZookeeperServer {

  private static TestingServer testingServer;

  public static void start(int port) throws Exception {
    testingServer =
        new TestingServer(
            port, new File(String.format("build/test_zk_data/%s/", System.nanoTime())));
  }

  public static void stop() throws Exception {
    if (testingServer != null) {
      testingServer.close();
    }
  }

  private EmbedZookeeperServer() {}
}
