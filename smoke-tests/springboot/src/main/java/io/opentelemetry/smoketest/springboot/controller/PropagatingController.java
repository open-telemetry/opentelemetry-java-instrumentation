/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.smoketest.springboot.controller;

import io.opentelemetry.api.trace.Span;
import java.net.URI;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * This controller demonstrates that context propagation works across http calls.
 * Calling <code>/front</code> should return a string which contains two traceId separated by ";".
 * First traceId was reported by <code>/front</code> handler, the second one was returned by
 * <code>/back</code> handler which was called by <code>/front</code>. If context propagation
 * works correctly, then both values should be the same.
 */
@RestController
public class PropagatingController {
  private final RestTemplate restTemplate;
  private final Environment environment;

  public PropagatingController(RestTemplateBuilder restTemplateBuilder, Environment environment) {
    this.restTemplate = restTemplateBuilder.build();
    this.environment = environment;
  }

  @RequestMapping("/front")
  public String front() {
    URI backend = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .port(environment.getProperty("local.server.port"))
        .path("/back")
        .build()
        .toUri();
    String backendTraceId = restTemplate.getForObject(backend, String.class);
    String frontendTraceId = Span.current().getSpanContext().getTraceId();
    return String.format("%s;%s", frontendTraceId, backendTraceId);
  }

  @RequestMapping("/back")
  public String back() {
    return Span.current().getSpanContext().getTraceId();
  }
}
