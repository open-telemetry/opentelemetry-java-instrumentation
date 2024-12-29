/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.ShutdownSignalException;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitMqTest extends AbstractRabbitMqTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  Connection conn;
  Channel channel;

  @BeforeEach
  public void setup() throws IOException, TimeoutException {
    conn = connectionFactory.newConnection();
    channel = conn.createChannel();
  }

  @AfterEach
  public void cleanup() throws IOException, TimeoutException {
    try {
      if (null != channel) {
        channel.close();
      }
      if (null != conn) {
        conn.close();
      }
    } catch (ShutdownSignalException ignored) {
      // ignored
    }
  }

  @Test
  void testRabbitPublishGet() throws IOException {
    String exchangeName = "some-exchange";
    String routingKey = "some-routing-key";

    String queueName =
        testing.runWithSpan(
            "producer parent",
            () -> {
              channel.exchangeDeclare(exchangeName, "direct", false);
              String internalQueueName = channel.queueDeclare().getQueue();
              channel.queueBind(internalQueueName, exchangeName, routingKey);
              channel.basicPublish(
                  exchangeName,
                  routingKey,
                  null,
                  "Hello, world!".getBytes(Charset.defaultCharset()));
              return internalQueueName;
            });
    GetResponse response =
        testing.runWithSpan(
            "consumer parent",
            () -> {
              return channel.basicGet(queueName, true);
            });

    assertEquals("Hello, world!", new String(response.getBody(), Charset.defaultCharset()));

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("producer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
                },
                span -> {
                  verifySpan(trace, span, 1, "exchange.declare", trace.getSpan(0));
                },
                span -> {
                  verifySpan(trace, span, 2, "queue.declare", trace.getSpan(0));
                },
                span -> {
                  verifySpan(trace, span, 3, "queue.bind", trace.getSpan(0));
                },
                span -> {
                  verifySpan(
                      trace,
                      span,
                      4,
                      exchangeName,
                      routingKey,
                      "publish",
                      exchangeName,
                      trace.getSpan(0));
                  producerSpan.set(trace.getSpan(4));
                }),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("consumer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
              },
              span -> {
                verifySpan(
                    trace,
                    span,
                    1,
                    exchangeName,
                    routingKey,
                    "receive",
                    "<generated>",
                    trace.getSpan(0),
                    producerSpan.get(),
                    null,
                    null,
                    false);
              });
        });
  }

  @Test
  void testRabbitPublishGetWithDefaultExchange() throws IOException {
    String queueName =
        testing.runWithSpan(
            "producer parent",
            () -> {
              String internalQueueName = channel.queueDeclare().getQueue();
              channel.basicPublish(
                  "", internalQueueName, null, "Hello, world!".getBytes(Charset.defaultCharset()));
              return internalQueueName;
            });
    GetResponse response =
        testing.runWithSpan(
            "consumer parent",
            () -> {
              return channel.basicGet(queueName, true);
            });

    assertEquals("Hello, world!", new String(response.getBody(), Charset.defaultCharset()));

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("producer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
                },
                span -> {
                  verifySpan(trace, span, 1, "queue.declare", trace.getSpan(0));
                },
                span -> {
                  verifySpan(
                      trace, span, 2, "<default>", null, "publish", "<default>", trace.getSpan(0));
                  producerSpan.set(trace.getSpan(2));
                }),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("consumer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
              },
              span -> {
                verifySpan(
                    trace,
                    span,
                    1,
                    "<default>",
                    null,
                    "receive",
                    "<generated>",
                    trace.getSpan(0),
                    producerSpan.get(),
                    null,
                    null,
                    false);
              });
        });
  }

  @ParameterizedTest(name = "test rabbit consume {1} messages and setTimestamp={2}")
  @MethodSource("provideParametersForMessageCountAndTimestamp")
  void testRabbitConsumeMessageCountAndSetTimestamp(
      String exchangeName, int messageCount, boolean setTimestamp) throws IOException {
    channel.exchangeDeclare(exchangeName, "direct", false);

    String queueName =
        (messageCount % 2 == 0)
            ? channel.queueDeclare().getQueue()
            : channel.queueDeclare("some-queue", false, true, true, null).getQueue();
    channel.queueBind(queueName, exchangeName, "");

    List<String> deliveries = new ArrayList<>();

    Consumer callback =
        new DefaultConsumer(channel) {
          @Override
          public void handleDelivery(
              String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
              throws IOException {
            deliveries.add(new String(body, Charset.defaultCharset()));
          }
        };

    channel.basicConsume(queueName, callback);

    for (int i = 1; i <= messageCount; i++) {
      if (setTimestamp) {
        channel.basicPublish(
            exchangeName,
            "",
            new AMQP.BasicProperties.Builder().timestamp(new Date()).build(),
            ("msg " + i).getBytes(Charset.defaultCharset()));
      } else {
        channel.basicPublish(
            exchangeName, "", null, ("msg " + i).getBytes(Charset.defaultCharset()));
      }
    }

    String resource = messageCount % 2 == 0 ? "<generated>" : queueName;

    List<java.util.function.Consumer<TraceAssert>> traceAssertions = new ArrayList<>();
    traceAssertions.add(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                verifySpan(trace, span, 0, "exchange.declare");
              });
        });
    traceAssertions.add(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                verifySpan(trace, span, 0, "queue.declare");
              });
        });
    traceAssertions.add(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                verifySpan(trace, span, 0, "queue.bind");
              });
        });
    traceAssertions.add(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                verifySpan(trace, span, 0, "basic.consume");
              });
        });

    for (int i = 1; i <= messageCount; i++) {
      traceAssertions.add(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, exchangeName, null, "publish", exchangeName);
                },
                span -> {
                  verifySpan(
                      trace,
                      span,
                      1,
                      exchangeName,
                      null,
                      "process",
                      resource,
                      trace.getSpan(0),
                      null,
                      null,
                      null,
                      setTimestamp);
                });
          });
    }

    testing.waitAndAssertTraces(traceAssertions);

    assertEquals(messageCount, deliveries.size());
    for (int i = 1; i <= messageCount; i++) {
      assertEquals("msg " + i, deliveries.get(i - 1));
    }
  }

  @Test
  void testRabbitConsumeError() throws IOException {
    String exchangeName = "some-error-exchange";
    FileNotFoundException error = new FileNotFoundException("Message Error");
    channel.exchangeDeclare(exchangeName, "direct", false);
    String queueName = channel.queueDeclare().getQueue();
    channel.queueBind(queueName, exchangeName, "");

    Consumer callback =
        new DefaultConsumer(channel) {
          @Override
          public void handleDelivery(
              String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
              throws IOException {
            throw error;
            // Unfortunately this doesn't seem to be observable in the test outside of the span
            // generated.
          }
        };

    channel.basicConsume(queueName, callback);

    channel.basicPublish(exchangeName, "", null, "msg".getBytes(Charset.defaultCharset()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, "exchange.declare");
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, "queue.declare");
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, "queue.bind");
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, "basic.consume");
                }),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                verifySpan(trace, span, 0, exchangeName, null, "publish", exchangeName);
              },
              span -> {
                verifySpan(
                    trace,
                    span,
                    1,
                    exchangeName,
                    null,
                    "process",
                    "<generated>",
                    trace.getSpan(0),
                    null,
                    error,
                    error.getMessage(),
                    false);
              });
        });
  }

  @SuppressWarnings("unchecked")
  @ParameterizedTest(name = "test rabbit error {0}")
  @MethodSource("provideParametersForCommandError")
  void testRabbitCommandError(ArgumentsAccessor accessor) {
    java.util.function.Consumer<Channel> callback =
        (java.util.function.Consumer<Channel>) accessor.get(4);
    Throwable thrown = null;
    try {
      callback.accept(channel);
    } catch (RuntimeException re) {
      thrown = re.getCause();
      assertTrue(thrown.getClass().getName().contains(accessor.getString(1)));
    }

    Throwable finalThrown = thrown;
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(
                      trace,
                      span,
                      0,
                      null,
                      null,
                      accessor.getString(3),
                      accessor.getString(0),
                      null,
                      null,
                      finalThrown,
                      accessor.getString(2),
                      false);
                }));
  }

  @Test
  void testSpringRabbit() {
    CachingConnectionFactory cachingConnectionFactory =
        new CachingConnectionFactory(connectionFactory);
    AmqpAdmin admin = new RabbitAdmin(cachingConnectionFactory);
    AmqpTemplate template = new RabbitTemplate(cachingConnectionFactory);

    Queue queue = new Queue("some-routing-queue", false, true, true, null);
    testing.runWithSpan(
        "producer parent",
        () -> {
          admin.declareQueue(queue);
          template.convertAndSend(queue.getName(), "foo");
        });

    String message =
        testing.runWithSpan(
            "consumer parent", () -> (String) template.receiveAndConvert(queue.getName()));

    assertEquals("foo", message);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("producer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
                },
                span -> {
                  verifySpan(trace, span, 1, "queue.declare", trace.getSpan(0));
                },
                span -> {
                  verifySpan(
                      trace,
                      span,
                      2,
                      "<default>",
                      "some-routing-queue",
                      "publish",
                      "<default>",
                      trace.getSpan(0));
                  producerSpan.set(trace.getSpan(2));
                }),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("consumer parent").hasKind(SpanKind.INTERNAL).hasNoParent();
              },
              span -> {
                verifySpan(
                    trace,
                    span,
                    1,
                    "<default>",
                    "some-routing-queue",
                    "receive",
                    queue.getName(),
                    trace.getSpan(0),
                    producerSpan.get(),
                    null,
                    null,
                    false);
              });
        });

    cachingConnectionFactory.destroy();
  }

  @Test
  void captureMessageHeaderAsSpanAttributes() throws IOException, InterruptedException {
    String queueName = channel.queueDeclare().getQueue();
    Map<String, Object> headers = new java.util.HashMap<>();
    headers.put("test-message-header", "test");
    AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(headers).build();
    channel.basicPublish(
        "", queueName, properties, "Hello, world!".getBytes(Charset.defaultCharset()));

    CountDownLatch latch = new CountDownLatch(1);
    List<String> deliveries = new ArrayList<>();

    Consumer callback =
        new DefaultConsumer(channel) {
          @Override
          public void handleDelivery(
              String consumerTag, Envelope envelope, AMQP.BasicProperties props, byte[] body)
              throws IOException {
            deliveries.add(new String(body, Charset.defaultCharset()));
            latch.countDown();
          }
        };

    channel.basicConsume(queueName, callback);
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    assertEquals("Hello, world!", deliveries.get(0));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, "queue.declare");
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, "<default>", null, "publish", "<default>");
                  span.hasAttributesSatisfying(
                      attributes -> {
                        assertThat(attributes)
                            .satisfies(
                                attrs -> {
                                  List<String> verifyHeaders =
                                      attrs.get(
                                          AttributeKey.stringArrayKey(
                                              "messaging.header.test_message_header"));
                                  assertNotNull(verifyHeaders);
                                  assertTrue(verifyHeaders.contains("test"));
                                });
                      });
                },
                span -> {
                  verifySpan(
                      trace,
                      span,
                      1,
                      "<default>",
                      null,
                      "process",
                      "<generated>",
                      trace.getSpan(0));
                  span.hasAttributesSatisfying(
                      attributes -> {
                        assertThat(attributes)
                            .satisfies(
                                attrs -> {
                                  List<String> verifyHeaders =
                                      attrs.get(
                                          AttributeKey.stringArrayKey(
                                              "messaging.header.test_message_header"));
                                  assertNotNull(verifyHeaders);
                                  assertTrue(verifyHeaders.contains("test"));
                                });
                      });
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  verifySpan(trace, span, 0, "basic.consume");
                }));
  }

  private static Stream<Arguments> provideParametersForMessageCountAndTimestamp() {
    return Stream.of(
        Arguments.of("some-exchange", 1, false),
        Arguments.of("some-exchange", 2, false),
        Arguments.of("some-exchange", 3, false),
        Arguments.of("some-exchange", 4, false),
        Arguments.of("some-exchange", 1, true),
        Arguments.of("some-exchange", 2, true),
        Arguments.of("some-exchange", 3, true),
        Arguments.of("some-exchange", 4, true));
  }

  private static Stream<Arguments> provideParametersForCommandError() {
    return Stream.of(
        Arguments.of(
            "exchange.declare",
            "IOException",
            null,
            null,
            (java.util.function.Consumer<Channel>)
                channel -> {
                  try {
                    channel.exchangeDeclare("some-exchange", "invalid-type", true);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }),
        Arguments.of(
            "Channel.basicConsume",
            "IllegalStateException",
            "Invalid configuration: 'queue' must be non-null.",
            null,
            (java.util.function.Consumer<Channel>)
                channel -> {
                  try {
                    channel.basicConsume(null, null);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }),
        Arguments.of(
            "<generated>",
            "IOException",
            null,
            "receive",
            (java.util.function.Consumer<Channel>)
                channel -> {
                  try {
                    channel.basicGet("amq.gen-invalid-channel", true);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
  }

  private static void verifySpan(
      TraceAssert trace, SpanDataAssert span, int index, String resource) {
    verifySpan(trace, span, index, null, null, null, resource, null);
  }

  private static void verifySpan(
      TraceAssert trace, SpanDataAssert span, int index, String resource, SpanData parentSpan) {
    verifySpan(trace, span, index, null, null, null, resource, parentSpan);
  }

  private static void verifySpan(
      TraceAssert trace,
      SpanDataAssert span,
      int index,
      String exchange,
      String routingKey,
      String operation,
      String resource) {
    verifySpan(trace, span, index, exchange, routingKey, operation, resource, null);
  }

  private static void verifySpan(
      TraceAssert trace,
      SpanDataAssert span,
      int index,
      String exchange,
      String routingKey,
      String operation,
      String resource,
      SpanData parentSpan) {
    verifySpan(
        trace,
        span,
        index,
        exchange,
        routingKey,
        operation,
        resource,
        parentSpan,
        null,
        null,
        null,
        false);
  }

  private static void verifySpan(
      TraceAssert trace,
      SpanDataAssert span,
      int index,
      String exchange,
      String routingKey,
      String operation,
      String resource,
      SpanData parentSpan,
      SpanData linkSpan,
      Throwable exception,
      String errorMsg,
      boolean expectTimestamp) {
    String spanName = resource;
    if (operation != null) {
      spanName += " " + operation;
    }

    String rabbitCommand =
        trace.getSpan(index).getAttributes().get(AttributeKey.stringKey("rabbitmq.command"));

    SpanKind spanKind = captureSpanKind(rabbitCommand);

    span.hasName(spanName).hasKind(spanKind);

    verifyParentAndLink(span, parentSpan, linkSpan);

    if (null != exception) {
      verifyException(span, exception, errorMsg);
    }

    verifyNetAttributes(span);
    verifyMessagingAttributes(span, exchange, routingKey, operation);

    if (expectTimestamp) {
      span.hasAttributesSatisfying(
          attributes -> {
            assertThat(attributes)
                .satisfies(
                    attrs -> {
                      Long timestamp =
                          attrs.get(AttributeKey.longKey("rabbitmq.record.queue_time_ms"));
                      assertNotNull(timestamp);
                      assertTrue(timestamp >= 0);
                    });
          });
    }

    if (null != rabbitCommand) {
      switch (rabbitCommand) {
        case "basic.publish":
          span.hasAttribute(AttributeKey.stringKey("rabbitmq.command"), "basic.publish")
              .hasAttributesSatisfying(
                  attributes -> {
                    assertThat(attributes)
                        .satisfies(
                            attrs -> {
                              String routingKeyAttr =
                                  attrs.get(
                                      MessagingIncubatingAttributes
                                          .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY);
                              assertTrue(
                                  routingKeyAttr == null
                                      || routingKeyAttr.equals("some-routing-key")
                                      || routingKeyAttr.equals("some-routing-queue")
                                      || routingKeyAttr.startsWith("amq.gen-"));

                              Long deliveryMode =
                                  attrs.get(AttributeKey.longKey("rabbitmq.delivery_mode"));
                              assertTrue(deliveryMode == null || deliveryMode == 2);

                              assertNotNull(attrs.get(MESSAGING_MESSAGE_BODY_SIZE));
                            });
                  });
          break;
        case "basic.get":
          span.hasAttribute(AttributeKey.stringKey("rabbitmq.command"), "basic.get")
              .hasAttributesSatisfying(
                  attributes -> {
                    assertThat(attributes)
                        .satisfies(
                            attrs -> {
                              // TODO why this queue name is not a destination for semantic
                              // convention
                              String queue = attrs.get(AttributeKey.stringKey("rabbitmq.queue"));
                              assertNotNull(queue);
                              assertTrue(
                                  queue.equals("some-queue")
                                      || queue.equals("some-routing-queue")
                                      || queue.startsWith("amq.gen-"));

                              attrs.get(MESSAGING_MESSAGE_BODY_SIZE);
                            });
                  });
          break;
        case "basic.deliver":
          span.hasAttribute(AttributeKey.stringKey("rabbitmq.command"), "basic.deliver")
              .hasAttributesSatisfying(
                  attributes -> {
                    assertThat(attributes)
                        .satisfies(
                            attrs -> {
                              attrs.get(MESSAGING_MESSAGE_BODY_SIZE);
                            });
                  });
          break;
        default:
          span.hasAttributesSatisfying(
              attributes -> {
                assertThat(attributes)
                    .satisfies(
                        attrs -> {
                          String command = attrs.get(AttributeKey.stringKey("rabbitmq.command"));
                          assertTrue(command == null || command.equals(resource));
                        });
              });
      }
    } else {
      span.hasAttributesSatisfying(
          attributes -> {
            assertThat(attributes)
                .satisfies(
                    attrs -> {
                      String command = attrs.get(AttributeKey.stringKey("rabbitmq.command"));
                      assertTrue(command == null || command.equals(resource));
                    });
          });
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void verifyMessagingAttributes(
      SpanDataAssert span, String exchange, String routingKey, String operation) {
    span.hasAttribute(MESSAGING_SYSTEM, "rabbitmq")
        .hasAttributesSatisfying(
            attributes -> {
              assertThat(attributes)
                  .satisfies(
                      attrs -> {
                        String destinationName = attrs.get(MESSAGING_DESTINATION_NAME);
                        assertTrue(destinationName == null || destinationName.equals(exchange));
                        String routingKeyAttr =
                            attrs.get(
                                MessagingIncubatingAttributes
                                    .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY);
                        assertTrue(
                            routingKeyAttr == null
                                || routingKeyAttr.equals(routingKey)
                                || routingKeyAttr.startsWith("amq.gen-"));
                      });
            });

    if (operation != null && !operation.equals("publish")) {
      span.hasAttribute(MESSAGING_OPERATION, operation);
    }
  }

  private static SpanKind captureSpanKind(String rabbitCommand) {
    SpanKind spanKind = SpanKind.CLIENT;

    if (null != rabbitCommand) {
      switch (rabbitCommand) {
        case "basic.publish":
          spanKind = SpanKind.PRODUCER;
          break;
        case "basic.get": // fallthrough
        case "basic.deliver":
          spanKind = SpanKind.CONSUMER;
          break;
        default:
          break;
      }
    }

    return spanKind;
  }

  private static void verifyNetAttributes(SpanDataAssert span) {
    span.hasAttributesSatisfying(
        attributes -> {
          assertThat(attributes)
              .satisfies(
                  attrs -> {
                    String peerAddr = attrs.get(NETWORK_PEER_ADDRESS);
                    assertThat(peerAddr).isIn(rabbitMqIp, null);

                    String networkType = attrs.get(NETWORK_TYPE);
                    assertThat(networkType).isIn("ipv4", "ipv6", null);

                    assertNotNull(attrs.get(NETWORK_PEER_PORT));
                  });
        });
  }

  private static void verifyException(SpanDataAssert span, Throwable exception, String errorMsg) {
    span.hasStatus(StatusData.error())
        .hasEventsSatisfying(
            events -> {
              assertThat(events.get(0))
                  .hasName("exception")
                  .hasAttributesSatisfying(
                      equalTo(EXCEPTION_TYPE, exception.getClass().getName()),
                      equalTo(EXCEPTION_MESSAGE, errorMsg),
                      satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class)));
            });
  }

  private static void verifyParentAndLink(
      SpanDataAssert span, SpanData parentSpan, SpanData linkSpan) {
    if (null != parentSpan) {
      span.hasParent(parentSpan);
    } else {
      span.hasNoParent();
    }

    if (null != linkSpan) {
      span.hasLinks(LinkData.create(linkSpan.getSpanContext()));
    } else {
      span.hasTotalRecordedLinks(0);
    }
  }
}
