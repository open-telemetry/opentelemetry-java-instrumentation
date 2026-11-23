/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import java.lang.management.ManagementFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
@Configuration(proxyBeanMethods = false)
public final class PetclinicStartupUptimeResponseAdvice implements ResponseBodyAdvice<Object> {
  private static final String READINESS_PATH = "/petclinic/api/owners/1";
  private static final String STARTUP_UPTIME_HEADER = "X-Startup-JVM-Uptime-Millis";
  private static final String JVM_VERSION_HEADER = "X-Startup-JVM-Version";

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (request.getMethod() == HttpMethod.GET
        && READINESS_PATH.equals(request.getURI().getPath())) {
      response
          .getHeaders()
          .set(
              STARTUP_UPTIME_HEADER,
              Long.toString(ManagementFactory.getRuntimeMXBean().getUptime()));
      response.getHeaders().set(JVM_VERSION_HEADER, System.getProperty("java.runtime.version"));
    }
    return body;
  }
}
