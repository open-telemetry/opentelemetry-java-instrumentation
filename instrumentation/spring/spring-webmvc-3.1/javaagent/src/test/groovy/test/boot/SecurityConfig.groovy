/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

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
  @Configuration
  @Order(1)
  static class ApiWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

    protected void configure(HttpSecurity http) throws Exception {
      http
        .csrf().disable()
        .antMatcher("/basicsecured/**")
        .authorizeRequests()
        .antMatchers("/basicsecured/**").authenticated()
        .and()
        .httpBasic()
        .and().authenticationProvider(applicationContext.getBean(SavingAuthenticationProvider));
    }
  }

  /**
   * Following configuration is required in order to get form login, needed by password tests
   */
  @Configuration
  static class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http
        .csrf().disable()
        .authorizeRequests()
        .antMatchers("/formsecured/**").authenticated()
        .and()
        .formLogin()
        .and().authenticationProvider(applicationContext.getBean(SavingAuthenticationProvider))
    }
  }
}

