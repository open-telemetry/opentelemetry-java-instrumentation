/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import boot.SavingAuthenticationProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

  @Bean
  SavingAuthenticationProvider savingAuthenticationProvider() {
    return new SavingAuthenticationProvider()
  }

  /**
   * Following configuration is required for unauthorised call tests (form would redirect, we need 401)
   */
  @Bean
  @Order(1)
  SecurityFilterChain apiWebSecurity(HttpSecurity http, SavingAuthenticationProvider savingAuthenticationProvider) {
    return http
      .csrf().disable()
      .securityMatcher("/basicsecured/**")
      .authorizeHttpRequests()
      .requestMatchers("/basicsecured/**").authenticated()
      .and()
      .httpBasic()
      .and()
      .authenticationProvider(savingAuthenticationProvider)
      .build()
  }

  /**
   * Following configuration is required in order to get form login, needed by password tests
   */
  @Bean
  SecurityFilterChain formLoginWebSecurity(HttpSecurity http, SavingAuthenticationProvider savingAuthenticationProvider) {
    return http
      .csrf().disable()
      .authorizeHttpRequests()
      .requestMatchers("/formsecured/**").authenticated()
      .anyRequest().permitAll()
      .and()
      .formLogin()
      .and()
      .authenticationProvider(savingAuthenticationProvider)
      .build()
  }
}
