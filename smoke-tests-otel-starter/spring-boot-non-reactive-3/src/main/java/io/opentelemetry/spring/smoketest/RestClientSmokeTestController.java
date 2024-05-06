/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class RestClientSmokeTestController {

  public static final String REST_CLIENT = "/rest-client";
  private final Optional<RestClient> restClient;

  public RestClientSmokeTestController(
      RestClient.Builder restClientBuilder, OtelSpringStarterSmokeTestController controller) {
    restClient = controller.getRootUri().map(uri -> restClientBuilder.baseUrl(uri).build());
  }

  @GetMapping(REST_CLIENT)
  public String restClient() {
    return restClient
        .map(
            c ->
                c.get()
                    .uri(OtelSpringStarterSmokeTestController.PING)
                    .retrieve()
                    .body(String.class))
        .orElseThrow(() -> new IllegalStateException("RestClient not available"));
  }
}
