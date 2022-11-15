/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.instrumentation;

import com.example.javaagent.bootstrap.AgentApi;

public final class DemoServlet3HelperClass {

  public static void doSomething(int number) {
    // call the api in bootstrap class loader
    AgentApi.doSomething(number);
  }

  private DemoServlet3HelperClass() {}
}
