/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle.v23_11;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;

import com.twitter.finagle.Http;
import com.twitter.finagle.service.RetryBudget;
import com.twitter.util.Duration;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests relevant client functionality.
 *
 * @implNote Why no http/2 tests: finagle maps everything down to http/1.1 via netty's own {@link
 *     Http2StreamFrameToHttpObjectCodec} which results in the same code path execution through
 *     finagle's netty stack. While testing would undoubtedly be beneficial, it's at this time
 *     untested due to lack of concrete support from the otel instrumentation test framework and
 *     upstream netty instrumentation, both.
 */
// todo implement http/2-specific tests;
//  otel test framework doesn't support an http/2 server out of the box
class ClientTest extends AbstractClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Http.Client createClient(ClientType clientType) {
    Http.Client client =
        Http.client()
            .withNoHttp2()
            .withTransport()
            .readTimeout(Duration.fromMilliseconds(READ_TIMEOUT.toMillis()))
            .withTransport()
            .connectTimeout(Duration.fromMilliseconds(CONNECTION_TIMEOUT.toMillis()))
            // disable automatic retries -- retries will result in under-counting traces in the
            // tests
            .withRetryBudget(RetryBudget.Empty());

    switch (clientType) {
      case TLS:
        client = client.withTransport().tlsWithoutValidation();
        break;
      case SINGLE_CONN:
        client = client.withSessionPool().maxSize(1);
        break;
      case DEFAULT:
        break;
    }

    return client;
  }
}
