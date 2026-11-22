/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.springboot.controller;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebController {
  private static final Logger logger = LoggerFactory.getLogger(WebController.class);
  private static final String STARTUP_UPTIME_HEADER = "X-Startup-JVM-Uptime-Millis";
  private static final String JVM_VERSION_HEADER = "X-Startup-JVM-Version";

  @RequestMapping("/greeting")
  public ResponseEntity<String> greeting() {
    logger.info("HTTP request received");
    String greeting = withSpan();
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        STARTUP_UPTIME_HEADER, Long.toString(ManagementFactory.getRuntimeMXBean().getUptime()));
    headers.set(JVM_VERSION_HEADER, System.getProperty("java.runtime.version"));
    return new ResponseEntity<>(greeting, headers, HttpStatus.OK);
  }

  @WithSpan
  public String withSpan() {
    return "Hi!";
  }
}
