/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
class EnduserAttributesHttpSecurityCustomizerTest {

  @Configuration
  static class TestConfiguration {}

  /**
   * Ensures that the {@link EnduserAttributesHttpSecurityCustomizer} registers a {@link
   * EnduserAttributesCapturingServletFilter} in the filter chain.
   *
   * <p>Usage of the filter is covered in other unit tests.
   */
  @Test
  void ensureFilterRegistered(@Autowired ApplicationContext applicationContext) throws Exception {

    HttpSecurity httpSecurity = createHttpSecurity(applicationContext);

    EnduserAttributesHttpSecurityCustomizer customizer =
        new EnduserAttributesHttpSecurityCustomizer(new EnduserAttributesCapturer());
    customizer.customize(httpSecurity);

    DefaultSecurityFilterChain filterChain = httpSecurity.build();

    assertThat(filterChain.getFilters())
        .filteredOn(EnduserAttributesCapturingServletFilter.class::isInstance)
        .hasSize(1);
  }

  private static HttpSecurity createHttpSecurity(ApplicationContext applicationContext)
      throws Exception {

    Class<?> processorClass = getObjectPostProcessorClass();
    Object processor = mock(processorClass);
    AuthenticationManagerBuilder authenticationBuilder =
        AuthenticationManagerBuilder.class.getConstructor(processorClass).newInstance(processor);

    return HttpSecurity.class
        .getConstructor(processorClass, AuthenticationManagerBuilder.class, Map.class)
        .newInstance(
            processor,
            authenticationBuilder,
            Collections.singletonMap(ApplicationContext.class, applicationContext));
  }

  private static Class<?> getObjectPostProcessorClass() throws ClassNotFoundException {
    try {
      return Class.forName("org.springframework.security.config.ObjectPostProcessor");
    } catch (ClassNotFoundException e) {
      // this was marked deprecated for removal in 6.4.2
      return Class.forName("org.springframework.security.config.annotation.ObjectPostProcessor");
    }
  }
}
