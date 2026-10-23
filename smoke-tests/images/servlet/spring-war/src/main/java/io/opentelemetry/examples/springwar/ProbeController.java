/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.examples.springwar;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ProbeController {
  @GetMapping(value = "/hello", produces = "text/plain")
  public String hello() {
    return "hello from Spring MVC\n";
  }
}
