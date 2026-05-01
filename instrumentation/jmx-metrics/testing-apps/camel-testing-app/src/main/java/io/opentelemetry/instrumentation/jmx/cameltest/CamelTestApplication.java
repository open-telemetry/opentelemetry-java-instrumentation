/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.cameltest;

import org.apache.camel.main.Main;

public class CamelTestApplication {
  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.configure().addRoutesBuilder(new CamelTestRouter());
    main.run(args);
  }

  private CamelTestApplication() {}
}
