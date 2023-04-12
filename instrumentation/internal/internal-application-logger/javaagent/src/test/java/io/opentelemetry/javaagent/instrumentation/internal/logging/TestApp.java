/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class TestApp {

  public static void main(String[] args) throws Exception {
    // pretend to do some work for a second
    TimeUnit.SECONDS.sleep(1);
    LoggerFactory.getLogger(TestApp.class).info("Done!");
  }
}
