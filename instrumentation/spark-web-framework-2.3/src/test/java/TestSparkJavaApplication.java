/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import spark.Spark;

public class TestSparkJavaApplication {

  public static void initSpark(int port) {
    Spark.port(port);
    Spark.get("/", (req, res) -> "Hello World");

    Spark.get("/param/:param", (req, res) -> "Hello " + req.params("param"));

    Spark.get(
        "/exception/:param",
        (req, res) -> {
          throw new RuntimeException(req.params("param"));
        });

    Spark.awaitInitialization();
  }
}
