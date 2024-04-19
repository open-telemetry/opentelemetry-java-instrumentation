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
  private final PlayerRepository playerRepository;

  public OtelReactiveSpringStarterSmokeTestController(PlayerRepository playerRepository) {
    this.playerRepository = playerRepository;
  }

  @GetMapping(WEBFLUX)
  public Mono<String> webflux() {
    return playerRepository
        .findById(1)
        .map(player -> "Player: " + player.getName() + " Age: " + player.getAge());
  }
}
