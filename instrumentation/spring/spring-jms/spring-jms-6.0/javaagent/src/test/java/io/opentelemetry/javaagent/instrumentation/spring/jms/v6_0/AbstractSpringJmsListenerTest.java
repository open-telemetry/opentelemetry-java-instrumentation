/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import jakarta.jms.ConnectionFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

abstract class AbstractSpringJmsListenerTest {
  private static final Logger logger = LoggerFactory.getLogger(AbstractSpringJmsListenerTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> broker;

  @BeforeAll
  static void setUp() {
    broker =
        new GenericContainer<>("apache/activemq-artemis:2.44.0")
            .withEnv("ARTEMIS_USER", "test")
            .withEnv("ARTEMIS_PASSWORD", "test")
            .withEnv("JAVA_TOOL_OPTIONS", "-Dbrokerconfig.maxDiskUsage=-1")
            .withExposedPorts(61616, 8161)
            .waitingFor(Wait.forLogMessage(".*Server is now active.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLogConsumer(new Slf4jLogConsumer(logger));
    broker.start();
    cleanup.deferAfterAll(broker);
  }

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  @SuppressWarnings("unchecked")
  void testSpringJmsListener(Class<?> configClass) throws Exception {
    // given
    SpringApplication app = new SpringApplication(configClass);
    app.setDefaultProperties(defaultConfig());
    ConfigurableApplicationContext applicationContext = app.run();
    cleanup.deferCleanup(applicationContext);
    awaitDurableSubscriptions(applicationContext);

    JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
    jmsTemplate.setPubSubDomain(true);
    String message = "hello there";

    // when
    testing.runWithSpan("parent", () -> jmsTemplate.convertAndSend("spring-jms-listener", message));

    // then
    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, SECONDS)).isEqualTo(message);

    assertSpringJmsListener();
  }

  abstract void assertSpringJmsListener();

  // the listener containers subscribe asynchronously after the application context has started, and
  // a message published to a topic before its durable subscription exists is never delivered
  static void awaitDurableSubscriptions(ApplicationContext applicationContext) {
    JmsListenerEndpointRegistry registry =
        applicationContext.getBean(JmsListenerEndpointRegistry.class);
    await()
        .until(
            () ->
                registry.getListenerContainers().stream()
                    .map(DefaultMessageListenerContainer.class::cast)
                    .allMatch(DefaultMessageListenerContainer::isRegisteredWithDestination));
  }

  static Map<String, Object> defaultConfig() {
    Map<String, Object> props = new HashMap<>();
    props.put("spring.jmx.enabled", false);
    props.put("spring.main.web-application-type", "none");
    props.put("test.broker-url", "tcp://" + broker.getHost() + ":" + broker.getMappedPort(61616));
    return props;
  }
}
