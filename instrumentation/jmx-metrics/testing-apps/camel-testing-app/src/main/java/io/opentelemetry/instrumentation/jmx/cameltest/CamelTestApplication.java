/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.cameltest;

import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelTestApplication {
  private static final Logger logger = LoggerFactory.getLogger(CamelTestApplication.class);

  private CamelTestApplication() {}

  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.configure().addRoutesBuilder(new CamelTestRouter());
    logger.info("Camel test application started.");
    main.run(args);
  }
}
