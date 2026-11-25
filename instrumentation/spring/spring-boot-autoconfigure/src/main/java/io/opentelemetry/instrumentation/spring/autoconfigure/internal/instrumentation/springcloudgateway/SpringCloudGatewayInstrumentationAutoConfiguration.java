/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.springcloudgateway;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.cloud.gateway.v3_0.SpringCloudGatewayRouteGetter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-cloud-gateway")
@ConditionalOnClass(name = "org.springframework.cloud.gateway.route.Route")
@Configuration
public class SpringCloudGatewayInstrumentationAutoConfiguration {
  public SpringCloudGatewayInstrumentationAutoConfiguration() {}

  @Bean
  @ConditionalOnMissingBean(SpringCloudGatewayRouteGetter.class)
  SpringCloudGatewayRouteGetter otelSpringCloudGatewayRouteGetter() {
    return new SpringCloudGatewayRouteGetter();
  }
}
