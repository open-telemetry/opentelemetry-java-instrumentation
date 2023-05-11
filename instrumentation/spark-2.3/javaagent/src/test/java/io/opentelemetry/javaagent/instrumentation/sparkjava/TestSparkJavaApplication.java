/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sparkjava;

import spark.Spark;

public class TestSparkJavaApplication {

  public static void initSpark(int port) {
    Spark.port(port);
    Spark.get("/", (req, res) -> "Hello World");

    Spark.get("/param/:param", (req, res) -> "Hello " + req.params("param"));

    Spark.get(
        "/exception/:param",
        (req, res) -> {
          throw new IllegalStateException(req.params("param"));
        });

    Spark.awaitInitialization();
  }

  private TestSparkJavaApplication() {}
}
