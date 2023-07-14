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
      AwsConnector awsConnector, Class<?> config, Map<String, Object> properties) {
    springApplication = new SpringApplication(config);
    springApplication.setDefaultProperties(properties);
    injectClients(awsConnector);
  }

  private void injectClients(AwsConnector awsConnector) {
    springApplication.addInitializers(
        (ApplicationContextInitializer<AbstractApplicationContext>)
            applicationContext -> {
              if (awsConnector.getSqsClient() != null) {
                applicationContext
                    .getBeanFactory()
                    .registerSingleton("sqsClient", awsConnector.getSqsClient());
              }
              if (awsConnector.getS3Client() != null) {
                applicationContext
                    .getBeanFactory()
                    .registerSingleton("s3Client", awsConnector.getS3Client());
              }
              if (awsConnector.getSnsClient() != null) {
                applicationContext
                    .getBeanFactory()
                    .registerSingleton("snsClient", awsConnector.getSnsClient());
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
