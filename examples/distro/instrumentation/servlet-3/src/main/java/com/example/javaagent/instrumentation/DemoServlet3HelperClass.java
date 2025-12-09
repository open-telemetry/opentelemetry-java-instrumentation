/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.instrumentation;

import com.example.javaagent.bootstrap.AgentApi;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletResponse;

public final class DemoServlet3HelperClass {

  public static final VirtualField<ServletResponse, AtomicInteger> VIRTUAL_FIELD =
      VirtualField.find(ServletResponse.class, AtomicInteger.class);

  public static void doSomething(int number) {
    // call the api in bootstrap class loader
    AgentApi.doSomething(number);
  }

  private DemoServlet3HelperClass() {}
}
