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
      // ignore
    } finally {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      Thread.sleep(1000L);
                      testingServer.close();
                    } catch (InterruptedException | IOException ignore) {
                      // ignore
                    }
                  }));
    }
  }

  private EmbedZookeeperServer() {}
}
