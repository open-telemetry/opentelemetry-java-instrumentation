/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestMain {

  private static final Logger logger = LogManager.getLogger();

  public static void main(String[] args) throws Exception {
    TestService service = new TestService();
    Server server =
        ServerBuilder.forPort(8080).addService(service).directExecutor().build().start();
    Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
    logger.info("Server started at port 8080.");
    server.awaitTermination();
  }
}
