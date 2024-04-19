/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class OtelSpringStarterWebfluxSmokeTestController {

  public static final String PING = "/ping";

  @GetMapping(PING)
  public Mono<String> getStock() {
    return Mono.just("pong");
  }
}
