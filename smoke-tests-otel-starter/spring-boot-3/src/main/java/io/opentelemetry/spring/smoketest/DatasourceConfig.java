/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.api.OpenTelemetry;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("user-has-defined-datasource-bean")
public class DatasourceConfig {

  @Bean
  public DataSource dataSource(OpenTelemetry openTelemetry) {
    BasicDataSource dataSource = new BasicDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl("jdbc:h2:mem:db");
    dataSource.setUsername("username");
    dataSource.setPassword("pwd");
    return dataSource;
  }
}
