/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.AbstractApplicationContext

class CamelSpringApp {

  private SpringApplication springApplication
  private ConfigurableApplicationContext context

  CamelSpringApp(AwsConnector awsConnector, Class config, Map<String, String> properties) {
    springApplication = new SpringApplication(config)
    springApplication.setDefaultProperties(properties)
    injectClients(awsConnector)
  }

  private injectClients(AwsConnector awsConnector) {
    springApplication.addInitializers(new ApplicationContextInitializer<AbstractApplicationContext>() {
      @Override
      void initialize(AbstractApplicationContext applicationContext) {
        if (awsConnector.getSnsClient() != null) {
          applicationContext.getBeanFactory().registerSingleton("snsClient", awsConnector.getSnsClient())
        }
        if (awsConnector.getSqsClient() != null) {
          applicationContext.getBeanFactory().registerSingleton("sqsClient", awsConnector.getSqsClient())
        }
        if (awsConnector.getS3Client() != null) {
          applicationContext.getBeanFactory().registerSingleton("s3Client", awsConnector.getS3Client())
        }
      }
    })
  }

  def start() {
    context = springApplication.run()
  }

  ProducerTemplate producerTemplate() {
    def camelContext = context.getBean(CamelContext)
    return camelContext.createProducerTemplate()
  }

  def stop() {
    if (context != null) {
      SpringApplication.exit(context)
    }
  }
}
