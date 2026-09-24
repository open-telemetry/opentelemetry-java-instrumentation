/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import java.net.InetSocketAddress;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Queue;
import org.apache.camel.Exchange;
import org.apache.camel.component.aws.sqs.SqsComponent;
import org.apache.camel.component.aws.sqs.SqsConfiguration;
import org.apache.camel.component.aws.sqs.SqsConsumer;
import org.apache.camel.component.aws.sqs.SqsEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqsCamelOwnershipInstrumentationTest {

  private static final boolean CAMEL_DISABLED = Boolean.getBoolean("testCamelDisabled");
  private static final boolean ADAPTER_DISABLED = Boolean.getBoolean("testAdapterDisabled");
  private static final String RECEIVE_RESPONSE =
      "<ReceiveMessageResponse xmlns=\"http://queue.amazonaws.com/doc/2012-11-05/\">"
          + "<ReceiveMessageResult><Message><MessageId>message-id</MessageId>"
          + "<ReceiptHandle>receipt-handle</ReceiptHandle>"
          + "<MD5OfBody>841a2d689ad86bd1611447453c22c6fc</MD5OfBody>"
          + "<Body>body</Body></Message></ReceiveMessageResult>"
          + "<ResponseMetadata><RequestId>request-id</RequestId></ResponseMetadata>"
          + "</ReceiveMessageResponse>";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void selectsCamelTraversalWithoutSuppressingOtherResponses() throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress("localhost", PortUtils.findOpenPort()), 0);
    server.createContext(
        "/",
        httpExchange -> {
          byte[] response = RECEIVE_RESPONSE.getBytes(UTF_8);
          httpExchange.getResponseHeaders().add("Content-Type", "text/xml");
          httpExchange.sendResponseHeaders(200, response.length);
          httpExchange.getResponseBody().write(response);
          httpExchange.close();
        });
    server.start();

    AmazonSQSAsyncClient client =
        (AmazonSQSAsyncClient)
            AmazonSQSAsyncClient.asyncBuilder()
                .withCredentials(
                    new AWSStaticCredentialsProvider(new BasicAWSCredentials("key", "secret")))
                .withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(
                        "http://localhost:" + server.getAddress().getPort(), "us-east-1"))
                .build();
    try {
      assertSeparateIndyHelperClassLoaders(client.getClass().getClassLoader());
      DefaultCamelContext camelContext = new DefaultCamelContext();
      SqsComponent component = new SqsComponent();
      component.setCamelContext(camelContext);
      SqsEndpoint endpoint = new SqsEndpoint("aws-sqs://test", component, new SqsConfiguration());
      TestSqsConsumer consumer = new TestSqsConsumer(endpoint);
      String queueUrl = "http://localhost:" + server.getAddress().getPort() + "/000000000000/test";
      testing.runWithSpan(
          "parent",
          () -> {
            ReceiveMessageResult result = client.receiveMessage(queueUrl);
            assertThat(consumer.exchanges(result.getMessages())).hasSize(1);
            for (Message message : client.receiveMessage(queueUrl).getMessages()) {
              testing.runWithSpan("nested", () -> assertThat(message.getBody()).isEqualTo("body"));
            }
          });
      testing.waitForTraces(1);
      assertThat(testing.spans())
          .filteredOn(
              span ->
                  span.getName().equals("process test") || span.getName().equals("test process"))
          .hasSize(emitStableMessagingSemconv() && !CAMEL_DISABLED && !ADAPTER_DISABLED ? 1 : 2);
      assertThat(testing.spans())
          .filteredOn(span -> span.getName().equals("nested"))
          .singleElement()
          .satisfies(
              nested ->
                  assertThat(testing.spans())
                      .filteredOn(span -> span.getSpanId().equals(nested.getParentSpanId()))
                      .singleElement()
                      .satisfies(
                          process ->
                              assertThat(process.getInstrumentationScopeInfo().getName())
                                  .isEqualTo("io.opentelemetry.aws-sdk-1.11")));
    } finally {
      client.shutdown();
      server.stop(0);
    }
  }

  private static void assertSeparateIndyHelperClassLoaders(ClassLoader applicationClassLoader)
      throws Exception {
    if (!Boolean.getBoolean("otel.javaagent.experimental.indy")) {
      return;
    }

    Class<?> registry =
        AgentClassLoaderAccess.loadClass(
            "io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry");
    Method getInstrumentationClassLoader =
        registry.getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class);
    ClassLoader camelClassLoader =
        (ClassLoader)
            getInstrumentationClassLoader.invoke(
                null,
                "io.opentelemetry.javaagent.instrumentation.camel.v2_20"
                    + ".ApacheCamelAwsSqsInstrumentationModule",
                applicationClassLoader);
    ClassLoader awsClassLoader =
        (ClassLoader)
            getInstrumentationClassLoader.invoke(
                null,
                "io.opentelemetry.javaagent.instrumentation.awssdk.v1_11"
                    + ".SqsInstrumentationModule",
                applicationClassLoader);
    assertThat(camelClassLoader).isNotSameAs(awsClassLoader);
  }

  private static class TestSqsConsumer extends SqsConsumer {
    TestSqsConsumer(SqsEndpoint endpoint) throws Exception {
      super(endpoint, exchange -> {});
    }

    Queue<Exchange> exchanges(List<Message> messages) {
      return createExchanges(messages);
    }
  }
}
