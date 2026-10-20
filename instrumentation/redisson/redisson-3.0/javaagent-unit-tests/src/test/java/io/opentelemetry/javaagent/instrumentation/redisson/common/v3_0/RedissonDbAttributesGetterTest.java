/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.nio.charset.StandardCharsets.US_ASCII;
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
    assertThat(extractedErrorType(error)).isEqualTo(RedisException.class.getName());
    assertThat(RedissonErrorCapture.currentServerErrorType().get()).isNull();
    assertThat(extractedErrorType(new RedisException("CUSTOM other operation")))
        .isEqualTo(RedisException.class.getName());
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
  }

  @Test
  void clientExceptionMessageIsNotClassifiedAsServerReply() {
    RedisException error = new RedisException("ERR connection refused");

    RedissonErrorCapture.captureServerError(error);

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isNull();
    assertThat(extractedErrorType(error)).isEqualTo(RedisException.class.getName());
  }

  @Test
  void missingMessageHasNoErrorType() {
    assertThat(GETTER.getErrorType(REQUEST, null, new RedisException())).isNull();
    assertThat(GETTER.getErrorType(REQUEST, null, null)).isNull();
  }

  @Test
  void dedicatedExceptionKeepsItsExceptionClassFallback() {
    RedisLoadingException error = new RedisLoadingException("LOADING Redis is loading");

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isNull();
    assertThat(extractedErrorType(error)).isEqualTo(RedisLoadingException.class.getName());
  }

  @Test
  void connectionExceptionIsNotClassifiedAsServerError() {
    RedisConnectionException error = new RedisConnectionException("ERR connection refused");

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isNull();
    assertThat(extractedErrorType(error)).isEqualTo(RedisConnectionException.class.getName());
  }

  private static String extractedErrorType(Throwable error) {
    AttributesBuilder attributes = Attributes.builder();
    EXTRACTOR.onEnd(attributes, Context.root(), REQUEST, null, error);
    return attributes.build().get(ERROR_TYPE);
  }
}
