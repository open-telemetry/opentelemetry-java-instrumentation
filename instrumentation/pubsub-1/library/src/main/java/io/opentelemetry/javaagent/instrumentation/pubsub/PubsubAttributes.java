package io.opentelemetry.javaagent.instrumentation.pubsub;

import io.opentelemetry.api.common.AttributeKey;

public final class PubsubAttributes {
  private PubsubAttributes() {}
  public static final AttributeKey<Long> MESSAGE_BODY_SIZE = AttributeKey.longKey("messaging.message.body.size");
  public static final AttributeKey<Long> MESSAGE_ENVELOPE_SIZE = AttributeKey.longKey("messaging.message.envelope.size");
  public static final AttributeKey<String> ORDERING_KEY = AttributeKey.stringKey("messaging.gcp_pubsub.message.ordering_key");
  public static final AttributeKey<String> ACK_RESULT = AttributeKey.stringKey("messaging.gcp_pubsub.message.ack_result");

  public static final class AckResultValues {
    private AckResultValues() {}
    public static final String ACK = "ack";
    public static final String NACK = "nack";
  }

  public static final class MessagingSystemValues {
    private MessagingSystemValues() {}
    public static final String GCP_PUBSUB = "gcp_pubsub";
  }
}
