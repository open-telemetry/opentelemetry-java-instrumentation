/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.H2Dialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;

@EnableR2dbcRepositories(
    basePackages = "io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository")
public class PersistenceConfig {

  @Bean
  ConnectionFactory connectionFactory() {
    return ConnectionFactories.find(
        ConnectionFactoryOptions.builder()
            .option(DRIVER, "h2")
            .option(PROTOCOL, "mem")
            .option(HOST, "localhost")
            .option(USER, "sa")
            .option(PASSWORD, "")
            .option(DATABASE, "db")
            .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
            .build());
  }

  @Bean
  ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
    ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
    initializer.setConnectionFactory(connectionFactory);
    initializer.setDatabasePopulator(
        new ResourceDatabasePopulator(
            new ByteArrayResource(
                ("CREATE TABLE customer (id INT PRIMARY KEY, firstname VARCHAR(100) NOT NULL, lastname VARCHAR(100) NOT NULL);"
                        + "INSERT INTO customer (id, firstname, lastname) VALUES ('1', 'First', 'Last');")
                    .getBytes(StandardCharsets.UTF_8))));

    return initializer;
  }

  @Bean
  public R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
    DatabaseClient databaseClient = DatabaseClient.create(connectionFactory);

    return new R2dbcEntityTemplate(databaseClient, H2Dialect.INSTANCE);
  }
}
