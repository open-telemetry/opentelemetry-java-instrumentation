/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.messaging.Message;

@SpringBootApplication
class SpringCloudGcpTestApplication {

  static volatile String emulatorHost;
  static volatile Consumer<String> messageHandler;

  @Bean
  MessageProducer pubsubInboundChannelAdapter(
      PubSubTemplate pubSubTemplate, @Qualifier("pubsubInputChannel") DirectChannel inputChannel) {
    PubSubInboundChannelAdapter adapter =
        new PubSubInboundChannelAdapter(pubSubTemplate, "test-subscription");
    adapter.setOutputChannel(inputChannel);
    adapter.setPayloadType(String.class);
    return adapter;
  }

  @Bean
  DirectChannel pubsubInputChannel() {
    return new DirectChannel();
  }

  @ServiceActivator(inputChannel = "pubsubInputChannel")
  void receiveMessage(Message<String> message) {
    Consumer<String> handler = messageHandler;
    if (handler != null) {
      handler.accept(message.getPayload());
    }
  }

  @Bean
  CredentialsProvider credentialsProvider() {
    // never look up application default credentials when running against the emulator
    return NoCredentialsProvider.create();
  }
}
