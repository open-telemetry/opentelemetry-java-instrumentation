/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public abstract class AbstractRabbitProducerConsumerTest {

  private GenericContainer<?> rabbitMqContainer;
  protected ConfigurableApplicationContext producerContext;
  private ConfigurableApplicationContext consumerContext;

  private final Class<?> additionalContextClass;

  public AbstractRabbitProducerConsumerTest(Class<?> additionalContextClass) {
    this.additionalContextClass = additionalContextClass;
  }

  @BeforeEach
  public void setUp() {
    startRabbit(additionalContextClass);
  }

  @AfterEach
  public void tearDown() {
    stopRabbit();
  }

  private void startRabbit(Class<?> additionalContext) {
    rabbitMqContainer =
        new GenericContainer<>("rabbitmq:latest")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));
    rabbitMqContainer.start();

    SpringApplication producerApp =
        new SpringApplication(getContextClasses(ProducerConfig.class, additionalContext));
    Map<String, Object> producerProperties = new HashMap<>();
    producerProperties.put("spring.application.name", "testProducer");
    producerProperties.put("spring.jmx.enabled", false);
    producerProperties.put("spring.main.web-application-type", "none");
    producerProperties.put("spring.rabbitmq.host", rabbitMqContainer.getHost());
    producerProperties.put("spring.rabbitmq.port", rabbitMqContainer.getMappedPort(5672));
    producerProperties.put("spring.cloud.stream.bindings.output.destination", "testTopic");
    producerApp.setDefaultProperties(producerProperties);
    producerContext = producerApp.run();

    SpringApplication consumerApp =
        new SpringApplication(
            getContextClasses(ProducerConfig.ConsumerConfig.class, additionalContext));
    Map<String, Object> consumerProperties = new HashMap<>();
    consumerProperties.put("spring.application.name", "testConsumer");
    consumerProperties.put("spring.jmx.enabled", false);
    consumerProperties.put("spring.main.web-application-type", "none");
    consumerProperties.put("spring.rabbitmq.host", rabbitMqContainer.getHost());
    consumerProperties.put("spring.rabbitmq.port", rabbitMqContainer.getMappedPort(5672));
    consumerProperties.put("spring.cloud.stream.bindings.input.destination", "testTopic");
    consumerApp.setDefaultProperties(consumerProperties);
    consumerContext = consumerApp.run();
  }

  private static Class<?>[] getContextClasses(Class<?> mainContext, Class<?> additionalContext) {
    List<Class<?>> contextClasses = new ArrayList<>();
    contextClasses.add(mainContext);
    if (additionalContext != null) {
      contextClasses.add(additionalContext);
    }
    return contextClasses.toArray(new Class<?>[0]);
  }

  private void stopRabbit() {
    rabbitMqContainer.stop();
    producerContext.close();
    consumerContext.close();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableBinding(Source.class)
  static class ProducerConfig {
    @Autowired Source source;

    @Bean
    Runnable producer() {
      return () ->
          runWithSpan(
              "producer",
              () -> {
                source.output().send(MessageBuilder.withPayload("test").build());
              });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableBinding(Sink.class)
    static class ConsumerConfig {
      @StreamListener(Sink.INPUT)
      void consume(String ignored) {
        runWithSpan(
            "consumer",
            () -> {
              // do nothing
            });
      }
    }
  }
}
