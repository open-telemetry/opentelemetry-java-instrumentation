/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.messaging;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.PROCESS_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.junit.jupiter.api.Test;

class MessagingTelemetryCarrierTest {

  private static final MessagingTelemetryCarrier<Message> messageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(Message.class, MessagingTelemetryClaims.class));
  private static final MessagingTelemetryCarrier<OtherMessage> otherMessageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(OtherMessage.class, MessagingTelemetryClaims.class));

  @Test
  void remembersClaimsPerObject() {
    Message message = new Message();
    Message otherMessage = new Message();

    messageTelemetry.claim(message, RECEIVE, CONSUMED_MESSAGES);

    assertThat(messageTelemetry.isClaimed(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.isClaimed(otherMessage, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  @Test
  void sharesClaimsBetweenAccessorsForTheSameType() {
    MessagingTelemetryCarrier<Message> otherAccessor =
        MessagingTelemetryCarrier.create(
            VirtualField.find(Message.class, MessagingTelemetryClaims.class));
    Message message = new Message();

    messageTelemetry.claim(message, RECEIVE, CONSUMED_MESSAGES);

    assertThat(otherAccessor.isClaimed(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void usesProvidedFieldForStorage() {
    MessagingTelemetryCarrier<Message> directFieldTelemetry =
        MessagingTelemetryCarrier.create(new DirectMessageField());
    Message message = new Message();

    directFieldTelemetry.claim(message, RECEIVE, CONSUMED_MESSAGES);

    assertThat(message.claims).isEqualTo(MessagingTelemetryClaims.of(RECEIVE, CONSUMED_MESSAGES));
  }

  @Test
  void updatesClaimsWhileHoldingTheCarrierLock() {
    MessagingTelemetryCarrier<Message> lockCheckingTelemetry =
        MessagingTelemetryCarrier.create(new LockCheckingMessageField());

    lockCheckingTelemetry.claim(new Message(), RECEIVE, CONSUMED_MESSAGES);
  }

  @Test
  void keepsSignalsOfTheSameObjectIndependent() {
    Message message = new Message();

    messageTelemetry.claim(message, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.claim(message, PROCESS, SPAN);

    assertThat(messageTelemetry.isClaimed(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.isClaimed(message, PROCESS, SPAN)).isTrue();
    assertThat(messageTelemetry.isClaimed(message, RECEIVE, SPAN)).isFalse();
  }

  @Test
  void mergeAddsToWhatTheTargetAlreadyHolds() {
    OtherMessage source = new OtherMessage();
    Message target = new Message();
    otherMessageTelemetry.claim(source, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.claim(target, PROCESS, PROCESS_DURATION);

    messageTelemetry.mergeFrom(otherMessageTelemetry, source, target);

    assertThat(messageTelemetry.isClaimed(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.isClaimed(target, PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(otherMessageTelemetry.isClaimed(source, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void mergingAnUnclaimedSourceKeepsTheTargetAsItWas() {
    Message target = new Message();
    messageTelemetry.claim(target, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.mergeFrom(otherMessageTelemetry, new OtherMessage(), target);

    assertThat(messageTelemetry.isClaimed(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void replaceMakesTheTargetMatchTheSource() {
    OtherMessage source = new OtherMessage();
    Message target = new Message();
    otherMessageTelemetry.claim(source, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.claim(target, PROCESS, PROCESS_DURATION);

    messageTelemetry.replaceFrom(otherMessageTelemetry, source, target);

    assertThat(messageTelemetry.isClaimed(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.isClaimed(target, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void replacingFromAnUnclaimedSourceEmptiesTheTarget() {
    Message target = new Message();
    messageTelemetry.claim(target, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.replaceFrom(otherMessageTelemetry, new OtherMessage(), target);

    assertThat(messageTelemetry.getClaims(target).isEmpty()).isTrue();
  }

  @Test
  void clearForgetsEverythingAboutTheObject() {
    Message message = new Message();
    messageTelemetry.claim(message, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.claim(message, PROCESS, SPAN);

    messageTelemetry.clear(message);

    assertThat(messageTelemetry.getClaims(message).isEmpty()).isTrue();
  }

  @Test
  void toleratesNullObjects() {
    Message message = new Message();
    messageTelemetry.claim(message, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.claim(null, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.mergeFrom(otherMessageTelemetry, new OtherMessage(), null);
    messageTelemetry.mergeFrom(otherMessageTelemetry, null, message);
    messageTelemetry.replaceFrom(otherMessageTelemetry, new OtherMessage(), null);
    messageTelemetry.replaceFrom(otherMessageTelemetry, null, null);
    messageTelemetry.clear(null);

    assertThat(messageTelemetry.getClaims(null)).isEqualTo(MessagingTelemetryClaims.none());
    assertThat(messageTelemetry.isClaimed(null, RECEIVE, CONSUMED_MESSAGES)).isFalse();
    assertThat(messageTelemetry.isClaimed(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void replacingFromNullEmptiesTheTarget() {
    Message target = new Message();
    messageTelemetry.claim(target, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.replaceFrom(otherMessageTelemetry, null, target);

    assertThat(messageTelemetry.getClaims(target).isEmpty()).isTrue();
  }

  private static class Message {
    private MessagingTelemetryClaims claims;
  }

  private static class OtherMessage {}

  private static class DirectMessageField extends VirtualField<Message, MessagingTelemetryClaims> {

    @Override
    public MessagingTelemetryClaims get(Message message) {
      return message.claims;
    }

    @Override
    public void set(Message message, MessagingTelemetryClaims claims) {
      message.claims = claims;
    }
  }

  private static class LockCheckingMessageField extends DirectMessageField {

    @Override
    public MessagingTelemetryClaims get(Message message) {
      assertThat(Thread.holdsLock(message)).isTrue();
      return super.get(message);
    }

    @Override
    public void set(Message message, MessagingTelemetryClaims claims) {
      assertThat(Thread.holdsLock(message)).isTrue();
      super.set(message, claims);
    }
  }
}
