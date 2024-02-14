/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import io.opentelemetry.api.common.AttributeKey;

public final class PubsubAttributes {
  private PubsubAttributes() {}

  /**
   * Already merged but not yet released:
   * https://github.com/open-telemetry/semantic-conventions/pull/545
   */
  public static final AttributeKey<String> ORDERING_KEY =
      AttributeKey.stringKey("messaging.gcp_pubsub.message.ordering_key");

  /** Coming soon: https://github.com/open-telemetry/semantic-conventions/pull/737 */
  public static final AttributeKey<String> ACK_RESULT =
      AttributeKey.stringKey("messaging.gcp_pubsub.message.ack_result");

  public static final class AckResultValues {
    private AckResultValues() {}

    public static final String ACK = "ack";
    public static final String NACK = "nack";
  }

  /**
   * Already merged but not yet released:
   * https://github.com/open-telemetry/semantic-conventions/pull/517
   */
  public static final class MessagingSystemValues {
    private MessagingSystemValues() {}

    public static final String GCP_PUBSUB = "gcp_pubsub";
  }
}
