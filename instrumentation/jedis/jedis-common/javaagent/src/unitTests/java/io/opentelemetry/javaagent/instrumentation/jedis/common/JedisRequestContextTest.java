/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.junit.jupiter.api.Test;

class JedisRequestContextTest {
  private static final Instrumenter<InitializationRequest, Void> instrumenter =
      Instrumenter.<InitializationRequest, Void>builder(
              OpenTelemetry.noop(), "test", request -> "test")
          .buildInstrumenter();

  @Test
  void returnedBatchFailuresUseFirstServerError() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    assertBatchCompletion(firstServerFailure, laterServerFailure, null, firstServerFailure);
  }

  @Test
  void terminalFirstServerFailureWins() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    assertBatchCompletion(
        firstServerFailure, laterServerFailure, firstServerFailure, firstServerFailure);
  }

  @Test
  void terminalLaterServerFailureWins() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    assertBatchCompletion(
        firstServerFailure, laterServerFailure, laterServerFailure, laterServerFailure);
  }

  @Test
  void terminalClientFailureWins() {
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    Throwable clientFailure = new IllegalStateException("WRONGTYPE client failure");
    assertBatchCompletion(firstServerFailure, laterServerFailure, clientFailure, clientFailure);
  }

  @Test
  void serverErrorConfirmationUsesThrowableIdentity() {
    JedisRequestContext<Object> context = JedisRequestContext.attachForBatch();
    Throwable firstServerFailure = new IllegalStateException("WRONGTYPE failure");
    Throwable terminalServerFailure = new IllegalStateException("WRONGTYPE failure");
    JedisRequestContext.captureServerError(firstServerFailure);
    JedisRequestContext.captureServerError(terminalServerFailure);

    assertThat(context.isServerError(firstServerFailure)).isTrue();
    assertThat(context.isServerError(terminalServerFailure)).isTrue();
    assertThat(context.isServerError(new IllegalStateException("WRONGTYPE failure"))).isFalse();
    assertThat(context.detachAndEnd(terminalServerFailure)).isSameAs(terminalServerFailure);
    assertDetached();
  }

  @Test
  void nestedBatchCaptureRestoresEnclosingContext() {
    JedisRequestContext<Object> outerContext = JedisRequestContext.attach();
    assertThat(JedisRequestContext.attach()).isNull();
    Throwable firstServerFailure = new IllegalStateException("first server failure");
    Throwable laterServerFailure = new IllegalStateException("later server failure");
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();

    JedisRequestContext.captureServerError(firstServerFailure);
    JedisRequestContext.captureServerError(laterServerFailure);
    assertThat(batchContext.detachAndEnd(null)).isSameAs(firstServerFailure);
    assertThat(JedisRequestContext.attach()).isNull();

    Throwable nextServerFailure = new IllegalStateException("next server failure");
    batchContext = JedisRequestContext.attachForBatch();
    JedisRequestContext.captureServerError(nextServerFailure);
    Throwable terminalClientFailure = new IllegalStateException("terminal client failure");
    assertThat(batchContext.detachAndEnd(terminalClientFailure)).isSameAs(terminalClientFailure);
    assertThat(JedisRequestContext.attach()).isNull();

    assertThat(outerContext.detachAndEnd(null)).isNull();
    assertDetached();
  }

  @Test
  void nestedCommandCaptureDoesNotFailEnclosingBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    JedisRequestContext<Object> nestedContext = JedisRequestContext.attach();
    Throwable nestedServerFailure = new IllegalStateException("nested server failure");

    JedisRequestContext.captureServerError(nestedServerFailure);
    assertThat(nestedContext.detachAndEnd(null)).isNull();
    assertThat(batchContext.isServerError(nestedServerFailure)).isTrue();
    assertThat(batchContext.detachAndEnd(null)).isNull();
    assertDetached();
  }

  @Test
  void caughtConnectionReplyServerErrorDoesNotFailEnclosingBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext<Object> replyContext =
          requireNonNull(JedisRequestContext.attachForConnectionReply(new Object()));
      Throwable serverFailure = new IllegalStateException("WRONGTYPE handled server failure");
      JedisRequestContext.captureServerError(serverFailure);

      assertThat(replyContext.detachAndEnd(serverFailure)).isSameAs(serverFailure);
      assertThat(batchContext.isServerError(serverFailure)).isTrue();
      assertThat(batchContext.detachAndEnd(null)).isNull();
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void owningConnectionRepliesStillFailBatchAfterIndependentReply() {
    Object connection = new Object();
    InitializationRequest request = new InitializationRequest();
    JedisRequestContext<InitializationRequest> batchContext =
        JedisRequestContext.startBatch(instrumenter, request, null, connection);
    try {
      assertThat(JedisRequestContext.attachForConnectionReply(connection)).isNull();
      JedisRequestContext<Object> independentReply =
          requireNonNull(JedisRequestContext.attachForConnectionReply(new Object()));
      Throwable handledFailure = new IllegalStateException("handled failure");
      JedisRequestContext.captureServerError(handledFailure);
      assertThat(independentReply.detachAndEnd(handledFailure)).isSameAs(handledFailure);
      JedisRequestContext.completePipelineReplies(new Object());
      assertThat(JedisRequestContext.attachForConnectionReply(connection)).isNull();
      Throwable batchFailure = new IllegalStateException("batch failure");
      JedisRequestContext.captureServerError(batchFailure);
      JedisRequestContext.completePipelineReplies(connection);
      JedisRequestContext<Object> callbackReply =
          requireNonNull(JedisRequestContext.attachForConnectionReply(connection));
      JedisRequestContext.captureServerError(handledFailure);
      callbackReply.detachAndEnd(handledFailure);
      assertThat(batchContext.detachAndEnd(null)).isSameAs(batchFailure);
      assertThat(request.isServerError(handledFailure)).isTrue();
      assertThat(request.isServerError(batchFailure)).isTrue();
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void transactionRetainsReplyOwnershipAfterQueuedAcknowledgments() {
    Object connection = new Object();
    InitializationRequest request = new InitializationRequest();
    JedisRequestContext<InitializationRequest> batchContext =
        JedisRequestContext.startBatch(instrumenter, request, new Object(), connection);
    try {
      JedisRequestContext.completePipelineReplies(connection);
      assertThat(JedisRequestContext.attachForConnectionReply(connection)).isNull();
      JedisRequestContext.completeTransactionReplies(new Object());
      assertThat(JedisRequestContext.attachForConnectionReply(connection)).isNull();
      Throwable serverFailure = new IllegalStateException("EXEC server failure");
      JedisRequestContext.captureServerError(serverFailure);
      JedisRequestContext.completeTransactionReplies(connection);
      JedisRequestContext<Object> callbackReply =
          requireNonNull(JedisRequestContext.attachForConnectionReply(connection));
      Throwable handledFailure = new IllegalStateException("handled callback failure");
      JedisRequestContext.captureServerError(handledFailure);
      callbackReply.detachAndEnd(handledFailure);
      assertThat(batchContext.detachAndEnd(null)).isSameAs(serverFailure);
      assertThat(request.isServerError(serverFailure)).isTrue();
      assertThat(request.isServerError(handledFailure)).isTrue();
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void connectionInitializationIsolatesAndRestoresEnclosingRequest() {
    JedisRequestContext<Object> outerContext = JedisRequestContext.attach();
    try {
      JedisRequestContext<Object> initializationContext =
          requireNonNull(JedisRequestContext.attachForConnectionInitialization());
      Throwable serverFailure = new IllegalStateException("initialization server failure");
      JedisRequestContext.captureServerError(serverFailure);

      assertThat(initializationContext.detachAndEnd(serverFailure)).isSameAs(serverFailure);
      assertThat(outerContext.isServerError(serverFailure)).isTrue();
      assertThat(JedisRequestContext.attach()).isNull();
      assertThat(outerContext.detachAndEnd(null)).isNull();
    } finally {
      outerContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void connectionInitializationCompletesEachCommandAtItsResponse() {
    JedisRequestContext<Object> outerContext = JedisRequestContext.attach();
    try {
      JedisRequestContext<Object> initializationContext =
          requireNonNull(JedisRequestContext.attachForConnectionInitialization());

      InitializationRequest authRequest = new InitializationRequest();
      JedisRequestContext.endIfNotAttached(instrumenter, Context.root(), authRequest, null);
      JedisRequestContext<InitializationRequest> authCommandContext =
          requireNonNull(JedisRequestContext.attachForConnectionInitializationCommand());
      assertThat(authCommandContext.detachAndEnd(null)).isNull();

      InitializationRequest selectRequest = new InitializationRequest();
      JedisRequestContext.endIfNotAttached(instrumenter, Context.root(), selectRequest, null);
      JedisRequestContext<InitializationRequest> selectCommandContext =
          requireNonNull(JedisRequestContext.attachForConnectionInitializationCommand());
      Throwable selectFailure = new IllegalStateException("SELECT failure");
      JedisRequestContext.captureServerError(selectFailure);

      assertThat(selectCommandContext.detachAndEnd(selectFailure)).isSameAs(selectFailure);
      assertThat(authRequest.isServerError(selectFailure)).isFalse();
      assertThat(selectRequest.isServerError(selectFailure)).isTrue();
      assertThat(initializationContext.detachAndEnd(null)).isNull();
      assertThat(outerContext.detachAndEnd(null)).isNull();
    } finally {
      outerContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void queuedInitializationResponsesCompleteTheirCommands() {
    JedisRequestContext<Object> outerContext = JedisRequestContext.attach();
    try {
      JedisRequestContext<Object> initializationContext =
          requireNonNull(JedisRequestContext.attachForConnectionInitialization());
      InitializationRequest successfulRequest = new InitializationRequest();
      InitializationRequest firstFailedRequest = new InitializationRequest();
      InitializationRequest secondFailedRequest = new InitializationRequest();
      JedisRequestContext.endIfNotAttached(instrumenter, Context.root(), successfulRequest, null);
      JedisRequestContext.endIfNotAttached(instrumenter, Context.root(), firstFailedRequest, null);
      JedisRequestContext.endIfNotAttached(instrumenter, Context.root(), secondFailedRequest, null);

      Throwable firstFailure = new IllegalStateException("first initialization failure");
      Throwable secondFailure = new IllegalStateException("second initialization failure");
      JedisRequestContext.captureServerError(firstFailure);
      JedisRequestContext.captureServerError(secondFailure);
      JedisRequestContext.completeConnectionInitializationCommands(
          asList("OK", firstFailure, secondFailure), null);

      assertThat(successfulRequest.isServerError(firstFailure)).isFalse();
      assertThat(firstFailedRequest.isServerError(firstFailure)).isTrue();
      assertThat(firstFailedRequest.isServerError(secondFailure)).isFalse();
      assertThat(secondFailedRequest.isServerError(firstFailure)).isFalse();
      assertThat(secondFailedRequest.isServerError(secondFailure)).isTrue();
      assertThat(initializationContext.detachAndEnd(null)).isNull();
      assertThat(outerContext.detachAndEnd(null)).isNull();
    } finally {
      outerContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void deferredNestedBatchServerErrorReturnedByEnclosingBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext<Object> nestedContext = JedisRequestContext.attachForBatch();
      Throwable firstFailure = new IllegalStateException("WRONGTYPE first nested failure");
      Throwable deferredFailure = new IllegalStateException("ERR deferred nested failure");
      JedisRequestContext.captureServerError(firstFailure);
      JedisRequestContext.captureServerError(deferredFailure);

      assertThat(nestedContext.detachAndEnd(null)).isSameAs(firstFailure);
      assertThat(batchContext.isServerError(firstFailure)).isTrue();
      assertThat(batchContext.isServerError(deferredFailure)).isTrue();
      assertThat(batchContext.detachAndEnd(null, asList(deferredFailure, "OK")))
          .isSameAs(deferredFailure);
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void handledNestedBatchServerErrorDoesNotFailEnclosingBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext<Object> nestedContext = JedisRequestContext.attachForBatch();
      Throwable failure = new IllegalStateException("WRONGTYPE handled nested failure");
      JedisRequestContext.captureServerError(failure);

      assertThat(nestedContext.detachAndEnd(null)).isSameAs(failure);
      assertThat(batchContext.isServerError(failure)).isTrue();
      assertThat(batchContext.detachAndEnd(null, asList("PONG", "OK"))).isNull();
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void escapingNestedCommandServerErrorReturnedByBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext<Object> nestedContext = requireNonNull(JedisRequestContext.attach());
      Throwable failure = new IllegalStateException("WRONGTYPE nested server failure");
      JedisRequestContext.captureServerError(failure);

      assertThat(nestedContext.detachAndEnd(failure)).isSameAs(failure);
      assertThat(batchContext.isServerError(failure)).isTrue();
      assertThat(batchContext.detachAndEnd(null, asList(failure, "OK"))).isSameAs(failure);
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void caughtNestedCommandServerErrorDoesNotFailBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext<Object> nestedContext = requireNonNull(JedisRequestContext.attach());
      Throwable failure = new IllegalStateException("WRONGTYPE handled server failure");
      JedisRequestContext.captureServerError(failure);

      assertThat(nestedContext.detachAndEnd(failure)).isSameAs(failure);
      assertThat(batchContext.detachAndEnd(null, asList("PONG", "OK"))).isNull();
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void escapingNestedCommandServerErrorThrownByBatch() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext<Object> nestedContext = requireNonNull(JedisRequestContext.attach());
      Throwable failure = new IllegalStateException("WRONGTYPE nested server failure");
      JedisRequestContext.captureServerError(failure);

      assertThat(nestedContext.detachAndEnd(failure)).isSameAs(failure);
      assertThat(batchContext.isServerError(failure)).isTrue();
      assertThat(batchContext.detachAndEnd(failure)).isSameAs(failure);
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void returnedBatchClientFailureIsNotConfirmedAsServerError() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    Throwable failure = new IllegalStateException("WRONGTYPE local conversion failure");
    try {
      assertThat(batchContext.detachAndEnd(null, asList("PONG", failure))).isSameAs(failure);
      assertThat(batchContext.isServerError(failure)).isFalse();
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void terminalFailureWinsOverReturnedBatchFailure() {
    JedisRequestContext<Object> batchContext = JedisRequestContext.attachForBatch();
    Throwable returnedFailure = new IllegalStateException("returned server failure");
    Throwable terminalFailure = new IllegalStateException("terminal client failure");
    try {
      JedisRequestContext.captureServerError(returnedFailure);
      assertThat(batchContext.detachAndEnd(terminalFailure, asList(returnedFailure)))
          .isSameAs(terminalFailure);
    } finally {
      batchContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void nestedTransactionCaptureUsesIdentityAndRestoresEnclosingOwner() {
    Object outerTransaction = new String("transaction");
    JedisRequestContext<Object> outerContext = JedisRequestContext.attachForBatch(outerTransaction);
    try {
      assertThat(JedisRequestContext.attachForTransaction(outerTransaction)).isNull();

      Throwable outerFailure = new IllegalStateException("outer transaction failure");
      JedisRequestContext.captureServerError(outerFailure);
      Object nestedTransaction = new String("transaction");
      JedisRequestContext<Object> nestedContext =
          requireNonNull(JedisRequestContext.attachForTransaction(nestedTransaction));
      try {
        Throwable nestedFailure = new IllegalStateException("nested transaction failure");
        JedisRequestContext.captureServerError(nestedFailure);
        assertThat(nestedContext.detachAndEnd(null)).isSameAs(nestedFailure);
      } finally {
        nestedContext.detachAndEnd(null);
      }

      assertThat(JedisRequestContext.attachForTransaction(outerTransaction)).isNull();
      assertThat(outerContext.detachAndEnd(null)).isSameAs(outerFailure);
    } finally {
      outerContext.detachAndEnd(null);
    }
    assertDetached();
  }

  @Test
  void propagatesCommandFailure() {
    JedisRequestContext<Object> context = JedisRequestContext.attach();
    Throwable clientFailure = new IllegalStateException("WRONGTYPE client failure");
    try {
      assertThat(context.detachAndEnd(clientFailure)).isSameAs(clientFailure);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static void assertBatchCompletion(
      Throwable firstServerFailure,
      Throwable laterServerFailure,
      Throwable terminalFailure,
      Throwable expectedFailure) {
    JedisRequestContext<Object> context = JedisRequestContext.attachForBatch();
    try {
      JedisRequestContext.captureServerError(firstServerFailure);
      JedisRequestContext.captureServerError(laterServerFailure);
      assertThat(context.detachAndEnd(terminalFailure)).isSameAs(expectedFailure);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static void assertDetached() {
    JedisRequestContext<Object> next = JedisRequestContext.attach();
    try {
      assertThat(next).isNotNull();
      assertThat(next.detachAndEnd(null)).isNull();
    } finally {
      if (next != null) {
        next.detachAndEnd(null);
      }
    }
  }

  private static class InitializationRequest implements JedisServerErrorRequest {
    private JedisServerErrorContext serverErrorContext;

    @Override
    public void setServerErrorContext(JedisServerErrorContext serverErrorContext) {
      this.serverErrorContext = serverErrorContext;
    }

    private boolean isServerError(Throwable throwable) {
      return serverErrorContext != null && serverErrorContext.isServerError(throwable);
    }
  }
}
