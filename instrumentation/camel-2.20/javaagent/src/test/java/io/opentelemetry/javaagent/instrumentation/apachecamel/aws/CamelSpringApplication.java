/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

class CamelSpringApplication {

  private final SpringApplication springApplication;
  private ConfigurableApplicationContext context;

  CamelSpringApplication(
      SqsConnector sqsConnector, Class<?> config, Map<String, Object> properties) {
    springApplication = new SpringApplication(config);
    springApplication.setDefaultProperties(properties);
    injectClients(sqsConnector);
  }

  private void injectClients(SqsConnector sqsConnector) {
    springApplication.addInitializers(
        (ApplicationContextInitializer<AbstractApplicationContext>)
            applicationContext -> {
              if (sqsConnector.getSqsClient() != null) {
                applicationContext
                    .getBeanFactory()
                    .registerSingleton("sqsClient", sqsConnector.getSqsClient());
              }
            });
  }

  void start() {
    context = springApplication.run();
  }

  ProducerTemplate producerTemplate() {
    CamelContext camelContext = context.getBean(CamelContext.class);
    return camelContext.createProducerTemplate();
  }

  void stop() {
    if (context != null) {
      SpringApplication.exit(context);
    }
  }
}
