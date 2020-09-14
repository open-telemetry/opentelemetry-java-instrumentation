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

