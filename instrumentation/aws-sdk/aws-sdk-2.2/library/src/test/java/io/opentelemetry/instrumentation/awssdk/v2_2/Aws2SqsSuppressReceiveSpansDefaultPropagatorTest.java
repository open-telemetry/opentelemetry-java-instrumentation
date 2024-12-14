package io.opentelemetry.instrumentation.awssdk.v2_2;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

class Aws2SqsSuppressReceiveSpansDefaultPropagatorTest extends Aws2SqsSuppressReceiveSpansTest {

  @BeforeAll
  static void setup() {
    AwsSdkTelemetryBuilder telemetryBuilder = AwsSdkTelemetry.builder(testing.getOpenTelemetry())
        .setCaptureExperimentalSpanAttributes(true);
    configure(telemetryBuilder);
    telemetry = telemetryBuilder.build();
  }

  static void configure(AwsSdkTelemetryBuilder telemetryBuilder) {}

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    return false;
  }

  @Test
  void testDuplicateTracingInterceptor() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(telemetry.newExecutionInterceptor())
        .addExecutionInterceptor(telemetry.newExecutionInterceptor())
        .build();
    builder.overrideConfiguration(overrideConfiguration);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);
    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest);

    assertThat(response.messages().size()).isEqualTo(1);
    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));

    assertSqsTraces(true, false);
  }
}
