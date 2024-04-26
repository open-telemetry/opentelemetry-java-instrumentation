/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class OtelReactiveSpringStarterSmokeTestController {

  public static final String WEBFLUX = "/webflux";

  @GetMapping(WEBFLUX)
  public Mono<String> webflux() {
    return Mono.just("webflux");
  }
}
