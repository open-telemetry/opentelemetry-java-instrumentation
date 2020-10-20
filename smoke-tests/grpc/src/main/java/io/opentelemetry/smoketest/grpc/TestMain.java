/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.grpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.grpc.ServerBuilder;

public class TestMain {

  private static final Logger logger = LogManager.getLogger();

  public static void main(String[] args) throws Exception {
    var service = new TestService();
    var server = ServerBuilder.forPort(8080).addService(service).directExecutor().build().start();
    Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
    logger.info("Server started at port 8080.");
    server.awaitTermination();
  }
}
