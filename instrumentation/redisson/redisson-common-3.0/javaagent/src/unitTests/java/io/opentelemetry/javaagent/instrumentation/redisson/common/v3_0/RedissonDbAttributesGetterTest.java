/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;

class RedissonDbAttributesGetterTest {

  private static final RedissonDbAttributesGetter GETTER = new RedissonDbAttributesGetter();
  private static final RedissonRequest REQUEST =
      RedissonRequest.create(null, new Object(), null, null);
  private static final AttributesExtractor<RedissonRequest, Void> EXTRACTOR =
      DbClientAttributesExtractor.create(GETTER);

  @Test
  void capturesUnknownServerPrefixFromRespReply() {
    ByteBuf response = Unpooled.copiedBuffer("-CUSTOM diagnostic details\r\n", US_ASCII);
    String previous =
        RedissonErrorCapture.currentServerErrorType()
            .set(RedissonErrorCapture.parseServerErrorType(response));
    RedisException error = new RedisException("CUSTOM diagnostic details. channel: redis");
    try {
      RedissonErrorCapture.captureServerError(error);
    } finally {
      RedissonErrorCapture.currentServerErrorType().restore(previous);
      response.release();
    }

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isEqualTo("CUSTOM");
    assertThat(extractedErrorType(error)).isEqualTo("CUSTOM");

    RedissonErrorCapture.clearServerError(error);
    assertThat(RedissonErrorCapture.getServerErrorType(error)).isNull();
    assertThat(GETTER.getErrorType(REQUEST, null, error)).isEqualTo(RedisException.class.getName());
    assertThat(extractedErrorType(error)).isEqualTo(RedisException.class.getName());
    RedisException uncapturedError = new RedisException("CUSTOM other operation");
    assertThat(RedissonErrorCapture.getServerErrorType(uncapturedError)).isNull();
    assertThat(GETTER.getErrorType(REQUEST, null, uncapturedError))
        .isEqualTo(RedisException.class.getName());
    assertThat(extractedErrorType(uncapturedError)).isEqualTo(RedisException.class.getName());
    assertThat(RedissonErrorCapture.currentServerErrorType().get()).isNull();
  }

  @Test
  void malformedAndNonErrorRepliesDoNotProduceServerErrorType() {
    ByteBuf success = Unpooled.copiedBuffer("+OK\r\n", US_ASCII);
    try {
      assertThat(RedissonErrorCapture.parseServerErrorType(success)).isNull();
    } finally {
      success.release();
    }

    ByteBuf emptyPrefix = Unpooled.copiedBuffer("- \r\n", US_ASCII);
    try {
      assertThat(RedissonErrorCapture.parseServerErrorType(emptyPrefix)).isNull();
    } finally {
      emptyPrefix.release();
    }

    ByteBuf incomplete = Unpooled.copiedBuffer("-CUSTOM", US_ASCII);
    try {
      assertThat(RedissonErrorCapture.parseServerErrorType(incomplete)).isNull();
    } finally {
      incomplete.release();
    }

    ByteBuf incompleteAfterToken = Unpooled.copiedBuffer("-CUSTOM details", US_ASCII);
    try {
      assertThat(RedissonErrorCapture.parseServerErrorType(incompleteAfterToken)).isNull();
    } finally {
      incompleteAfterToken.release();
    }

    ByteBuf emptyReply = Unpooled.copiedBuffer("-\r\n", US_ASCII);
    try {
      assertThat(RedissonErrorCapture.parseServerErrorType(emptyReply)).isNull();
    } finally {
      emptyReply.release();
    }

    ByteBuf emptyToken = Unpooled.copiedBuffer("-   \r\n", US_ASCII);
    try {
      assertThat(RedissonErrorCapture.parseServerErrorType(emptyToken)).isNull();
    } finally {
      emptyToken.release();
    }
  }

  @Test
  void parsingStopsAtTheReplyTerminatorWithoutConsumingTheBuffer() {
    ByteBuf response =
        Unpooled.copiedBuffer("ignored:-PréfixOnly\r\n-SECOND diagnostic\r\n", UTF_8);
    response.readerIndex(8);
    try {
      assertThat(RedissonErrorCapture.parseServerErrorType(response)).isEqualTo("PréfixOnly");
      assertThat(response.readerIndex()).isEqualTo(8);
    } finally {
      response.release();
    }
  }

  @Test
  void clientExceptionMessageIsNotClassifiedAsServerReply() {
    RedisException error = new RedisException("ERR connection refused");

    RedissonErrorCapture.captureServerError(error);

    assertThat(RedissonErrorCapture.getServerErrorType(error)).isNull();
    assertThat(GETTER.getErrorType(REQUEST, null, error)).isEqualTo(RedisException.class.getName());
    assertThat(extractedErrorType(error)).isEqualTo(RedisException.class.getName());
    assertThat(GETTER.getErrorType(REQUEST, null, null)).isNull();
    assertThat(extractedErrorType(null)).isNull();
  }

  @Test
  void dedicatedExceptionUsesCapturedServerPrefixAndClearsIt() {
    RedisLoadingException error = new RedisLoadingException("LOADING Redis is loading");
    ByteBuf response = Unpooled.copiedBuffer("-LOADING Redis is loading\r\n", US_ASCII);
    String previous =
        RedissonErrorCapture.currentServerErrorType()
            .set(RedissonErrorCapture.parseServerErrorType(response));
    try {
      RedissonErrorCapture.captureServerError(error);
    } finally {
      RedissonErrorCapture.currentServerErrorType().restore(previous);
      response.release();
    }

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isEqualTo("LOADING");
    assertThat(extractedErrorType(error)).isEqualTo("LOADING");

    RedissonErrorCapture.clearServerError(error);
    assertThat(RedissonErrorCapture.getServerErrorType(error)).isNull();
    assertThat(GETTER.getErrorType(REQUEST, null, error))
        .isEqualTo(RedisLoadingException.class.getName());
    assertThat(extractedErrorType(error)).isEqualTo(RedisLoadingException.class.getName());
    assertThat(RedissonErrorCapture.currentServerErrorType().get()).isNull();
  }

  @Test
  void capturedPrefixIsConsumedBeforeCompletionCallbacks() {
    RedisException serverError = new RedisException("CUSTOM diagnostic details");
    RedisException clientError = new RedisException("client-side failure");
    String previous = RedissonErrorCapture.currentServerErrorType().set("CUSTOM");
    try {
      RedissonErrorCapture.captureServerError(serverError);
      // a callback issuing a new, client-side failing command within the decoder frame
      RedissonErrorCapture.captureServerError(clientError);
      assertThat(RedissonErrorCapture.currentServerErrorType().get()).isNull();
      // a repeated capture, as with BatchCommandData calling super.tryFailure, keeps the prefix
      RedissonErrorCapture.captureServerError(serverError);
    } finally {
      RedissonErrorCapture.currentServerErrorType().restore(previous);
    }

    assertThat(extractedErrorType(serverError)).isEqualTo("CUSTOM");
    assertThat(GETTER.getErrorType(REQUEST, null, clientError))
        .isEqualTo(RedisException.class.getName());
    assertThat(extractedErrorType(clientError)).isEqualTo(RedisException.class.getName());
  }

  @Test
  void connectionExceptionIsNotClassifiedAsServerError() {
    RedisConnectionException error = new RedisConnectionException("ERR connection refused");

    assertThat(RedissonErrorCapture.getServerErrorType(error)).isNull();
    assertThat(GETTER.getErrorType(REQUEST, null, error))
        .isEqualTo(RedisConnectionException.class.getName());
    assertThat(extractedErrorType(error)).isEqualTo(RedisConnectionException.class.getName());
  }

  private static String extractedErrorType(Throwable error) {
    AttributesBuilder attributes = Attributes.builder();
    EXTRACTOR.onEnd(attributes, Context.root(), REQUEST, null, error);
    return attributes.build().get(ERROR_TYPE);
  }
}
