/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0.boot;

import io.opentelemetry.instrumentation.spring.webmvc.boot.SavingAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SavingAuthenticationProvider savingAuthenticationProvider() {
    return new SavingAuthenticationProvider();
  }

  /**
   * Following configuration is required for unauthorised call tests (form would redirect, we need
   * 401)
   */
  @Bean
  @Order(1)
  SecurityFilterChain apiWebSecurity(
      HttpSecurity http, SavingAuthenticationProvider savingAuthenticationProvider)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .securityMatcher("/basicsecured/**")
        .authorizeHttpRequests(auth -> auth.requestMatchers("/basicsecured/**").authenticated())
        .authenticationProvider(savingAuthenticationProvider)
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  /** Following configuration is required in order to get form login, needed by password tests */
  @Bean
  SecurityFilterChain formLoginWebSecurity(
      HttpSecurity http, SavingAuthenticationProvider savingAuthenticationProvider)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/formsecured/**").authenticated().anyRequest().permitAll())
        .authenticationProvider(savingAuthenticationProvider)
        .formLogin(Customizer.withDefaults())
        .build();
  }
}
