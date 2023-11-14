/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
class EnduserAttributesHttpSecurityCustomizerTest {

  @Configuration
  static class TestConfiguration {}

  @Mock ObjectPostProcessor<Object> objectPostProcessor;

  /**
   * Ensures that the {@link EnduserAttributesHttpSecurityCustomizer} registers a {@link
   * EnduserAttributesCapturingServletFilter} in the filter chain.
   *
   * <p>Usage of the filter is covered in other unit tests.
   */
  @Test
  void ensureFilterRegistered(@Autowired ApplicationContext applicationContext) throws Exception {

    AuthenticationManagerBuilder authenticationBuilder =
        new AuthenticationManagerBuilder(objectPostProcessor);
    HttpSecurity httpSecurity =
        new HttpSecurity(
            objectPostProcessor,
            authenticationBuilder,
            Collections.singletonMap(ApplicationContext.class, applicationContext));

    EnduserAttributesHttpSecurityCustomizer customizer =
        new EnduserAttributesHttpSecurityCustomizer(new EnduserAttributesCapturer());
    customizer.customize(httpSecurity);

    DefaultSecurityFilterChain filterChain = httpSecurity.build();

    assertThat(filterChain.getFilters())
        .filteredOn(EnduserAttributesCapturingServletFilter.class::isInstance)
        .hasSize(1);
  }
}
