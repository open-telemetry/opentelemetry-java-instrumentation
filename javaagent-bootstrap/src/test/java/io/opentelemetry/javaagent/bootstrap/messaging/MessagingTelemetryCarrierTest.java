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

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.junit.jupiter.api.Test;

class MessagingTelemetryCarrierTest {

  private static final MessagingTelemetryCarrier<Message> messageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(Message.class, MessagingTelemetrySignals.class));
  private static final MessagingTelemetryCarrier<OtherMessage> otherMessageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(OtherMessage.class, MessagingTelemetrySignals.class));

  @Test
  void remembersSignalsPerObject() {
    Message message = new Message();
    Message otherMessage = new Message();

    messageTelemetry.add(message, RECEIVE, CONSUMED_MESSAGES);

    assertThat(messageTelemetry.contains(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.contains(otherMessage, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  @Test
  void sharesSignalsBetweenAccessorsForTheSameType() {
    MessagingTelemetryCarrier<Message> otherAccessor =
        MessagingTelemetryCarrier.create(
            VirtualField.find(Message.class, MessagingTelemetrySignals.class));
    Message message = new Message();

    messageTelemetry.add(message, RECEIVE, CONSUMED_MESSAGES);

    assertThat(otherAccessor.contains(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void usesProvidedFieldForStorage() {
    MessagingTelemetryCarrier<Message> directFieldTelemetry =
        MessagingTelemetryCarrier.create(new DirectMessageField());
    Message message = new Message();

    directFieldTelemetry.add(message, RECEIVE, CONSUMED_MESSAGES);

    assertThat(message.signals).isEqualTo(MessagingTelemetrySignals.of(RECEIVE, CONSUMED_MESSAGES));
  }

  @Test
  void updatesSignalsWhileHoldingTheCarrierLock() {
    MessagingTelemetryCarrier<Message> lockCheckingTelemetry =
        MessagingTelemetryCarrier.create(new LockCheckingMessageField());

    lockCheckingTelemetry.add(new Message(), RECEIVE, CONSUMED_MESSAGES);
  }

  @Test
  void keepsSignalsOfTheSameObjectIndependent() {
    Message message = new Message();

    messageTelemetry.add(message, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.add(message, PROCESS, SPAN);

    assertThat(messageTelemetry.contains(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.contains(message, PROCESS, SPAN)).isTrue();
    assertThat(messageTelemetry.contains(message, RECEIVE, SPAN)).isFalse();
  }

  @Test
  void mergeAddsToWhatTheTargetAlreadyHolds() {
    OtherMessage source = new OtherMessage();
    Message target = new Message();
    otherMessageTelemetry.add(source, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.add(target, PROCESS, PROCESS_DURATION);

    messageTelemetry.mergeFrom(otherMessageTelemetry, source, target);

    assertThat(messageTelemetry.contains(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.contains(target, PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(otherMessageTelemetry.contains(source, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void mergingAnEmptySourceKeepsTheTargetAsItWas() {
    Message target = new Message();
    messageTelemetry.add(target, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.mergeFrom(otherMessageTelemetry, new OtherMessage(), target);

    assertThat(messageTelemetry.contains(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void replaceMakesTheTargetMatchTheSource() {
    OtherMessage source = new OtherMessage();
    Message target = new Message();
    otherMessageTelemetry.add(source, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.add(target, PROCESS, PROCESS_DURATION);

    messageTelemetry.replaceFrom(otherMessageTelemetry, source, target);

    assertThat(messageTelemetry.contains(target, RECEIVE, CONSUMED_MESSAGES)).isTrue();
    assertThat(messageTelemetry.contains(target, PROCESS, PROCESS_DURATION)).isFalse();
  }

  @Test
  void replacingFromAnEmptySourceEmptiesTheTarget() {
    Message target = new Message();
    messageTelemetry.add(target, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.replaceFrom(otherMessageTelemetry, new OtherMessage(), target);

    assertThat(messageTelemetry.getSignals(target).isEmpty()).isTrue();
  }

  @Test
  void clearForgetsEverythingAboutTheObject() {
    Message message = new Message();
    messageTelemetry.add(message, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.add(message, PROCESS, SPAN);

    messageTelemetry.clear(message);

    assertThat(messageTelemetry.getSignals(message).isEmpty()).isTrue();
  }

  @Test
  void toleratesNullObjects() {
    Message message = new Message();
    messageTelemetry.add(message, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.add(null, RECEIVE, CONSUMED_MESSAGES);
    messageTelemetry.mergeFrom(otherMessageTelemetry, new OtherMessage(), null);
    messageTelemetry.mergeFrom(otherMessageTelemetry, null, message);
    messageTelemetry.replaceFrom(otherMessageTelemetry, new OtherMessage(), null);
    messageTelemetry.replaceFrom(otherMessageTelemetry, null, null);
    messageTelemetry.clear(null);

    assertThat(messageTelemetry.getSignals(null)).isEqualTo(MessagingTelemetrySignals.none());
    assertThat(messageTelemetry.contains(null, RECEIVE, CONSUMED_MESSAGES)).isFalse();
    assertThat(messageTelemetry.contains(message, RECEIVE, CONSUMED_MESSAGES)).isTrue();
  }

  @Test
  void replacingFromNullEmptiesTheTarget() {
    Message target = new Message();
    messageTelemetry.add(target, RECEIVE, CONSUMED_MESSAGES);

    messageTelemetry.replaceFrom(otherMessageTelemetry, null, target);

    assertThat(messageTelemetry.getSignals(target).isEmpty()).isTrue();
  }

  private static class Message {
    private MessagingTelemetrySignals signals;
  }

  private static class OtherMessage {}

  private static class DirectMessageField extends VirtualField<Message, MessagingTelemetrySignals> {

    @Override
    public MessagingTelemetrySignals get(Message message) {
      return message.signals;
    }

    @Override
    public void set(Message message, MessagingTelemetrySignals signals) {
      message.signals = signals;
    }
  }

  private static class LockCheckingMessageField extends DirectMessageField {

    @Override
    public MessagingTelemetrySignals get(Message message) {
      assertThat(Thread.holdsLock(message)).isTrue();
      return super.get(message);
    }

    @Override
    public void set(Message message, MessagingTelemetrySignals signals) {
      assertThat(Thread.holdsLock(message)).isTrue();
      super.set(message, signals);
    }
  }
}
