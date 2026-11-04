/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

class JedisErrorTypeTest {

  @Test
  void confirmedServerErrorUsesPrefix() {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details");
    JedisRequest request = requestFor(serverError);

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, serverError))
        .isEqualTo("WRONGTYPE");
  }

  @Test
  void unconfirmedServerErrorKeepsExceptionClassFallback() {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details");
    JedisDataException error = new JedisDataException("WRONGTYPE details");
    JedisRequest request = requestFor(serverError);

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, error))
        .isEqualTo(error.getClass().getName());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "WRONGTYPEé details"})
  void unusableServerErrorPrefixKeepsExceptionClassFallback(String message) {
    JedisDataException serverError = new JedisDataException(message);
    JedisRequest request = requestFor(serverError);

    assertThat(JedisServerError.errorType(serverError)).isNull();
    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, serverError))
        .isEqualTo(serverError.getClass().getName());
  }

  @Test
  void dedicatedServerErrorKeepsExceptionClassFallback() {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details") {};
    JedisRequest request = requestFor(serverError);

    assertThat(JedisServerError.errorType(serverError)).isNull();
    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, serverError))
        .isEqualTo(serverError.getClass().getName());
  }

  @Test
  void clientFailureKeepsExceptionClassFallback() {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details");
    JedisConnectionException error = new JedisConnectionException("client failure");
    JedisRequest request = requestFor(serverError);

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, error))
        .isEqualTo(error.getClass().getName());
  }

  @Test
  void successfulCommandHasNoErrorType() {
    JedisRequest request = requestFor(new JedisDataException("WRONGTYPE details"));

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, null)).isNull();
  }

  private static JedisRequest requestFor(Throwable serverError) {
    JedisRequest request = JedisRequest.create(new Connection(), Protocol.Command.GET, emptyList());
    request.setServerErrorContext(error -> error == serverError);
    return request;
  }
}
