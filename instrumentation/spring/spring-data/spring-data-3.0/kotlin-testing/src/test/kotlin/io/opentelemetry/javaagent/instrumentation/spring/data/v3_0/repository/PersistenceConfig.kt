/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.dialect.H2Dialect
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
import org.springframework.r2dbc.core.DatabaseClient
import java.nio.charset.StandardCharsets

@EnableR2dbcRepositories(basePackages = ["io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository"])
class PersistenceConfig {

  @Bean
  fun connectionFactory(): ConnectionFactory? = ConnectionFactories.find(
    ConnectionFactoryOptions.builder()
      .option(ConnectionFactoryOptions.DRIVER, "h2")
      .option(ConnectionFactoryOptions.PROTOCOL, "mem")
      .option(ConnectionFactoryOptions.HOST, "localhost")
      .option(ConnectionFactoryOptions.USER, "sa")
      .option(ConnectionFactoryOptions.PASSWORD, "")
      .option(ConnectionFactoryOptions.DATABASE, "db")
      .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
      .build()
  )

  @Bean
  fun initializer(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
    val initializer = ConnectionFactoryInitializer()
    initializer.setConnectionFactory(connectionFactory)
    initializer.setDatabasePopulator(
      ResourceDatabasePopulator(
        ByteArrayResource(
          ("CREATE TABLE customer (id INT PRIMARY KEY, name VARCHAR(100) NOT NULL);" +
            "INSERT INTO customer (id, name) VALUES ('1', 'Name');")
            .toByteArray(StandardCharsets.UTF_8)
        )
      )
    )

    return initializer
  }

  @Bean
  fun r2dbcEntityTemplate(connectionFactory: ConnectionFactory): R2dbcEntityTemplate {
    val databaseClient = DatabaseClient.create(connectionFactory)

    return R2dbcEntityTemplate(databaseClient, H2Dialect.INSTANCE)
  }
}
