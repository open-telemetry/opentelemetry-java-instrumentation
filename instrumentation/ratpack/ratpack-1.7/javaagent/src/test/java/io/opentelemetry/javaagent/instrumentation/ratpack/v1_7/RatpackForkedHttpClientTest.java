/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ConnectTimeoutException;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.ratpack.client.AbstractRatpackForkedHttpClientTest;
import io.opentelemetry.instrumentation.ratpack.v1_7.OpenTelemetryExecInitializer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.NetworkAttributes;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.exec.internal.DefaultExecController;
import ratpack.http.client.HttpClientReadTimeoutException;

class RatpackForkedHttpClientTest extends AbstractRatpackForkedHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @BeforeAll
  @Override
  protected void setUpClient() throws Exception {
    exec.run(
        unused -> {
          ((DefaultExecController) exec.getController())
              .setInitializers(ImmutableList.of(OpenTelemetryExecInitializer.INSTANCE));
          client = buildHttpClient();
          singleConnectionClient = buildHttpClient(spec -> spec.poolSize(1));
        });
  }

  @Override
  protected Set<AttributeKey<?>> computeHttpAttributes(URI uri) {
    Set<AttributeKey<?>> attributes = new HashSet<>(super.computeHttpAttributes(uri));
    attributes.remove(NetworkAttributes.NETWORK_PROTOCOL_VERSION);
    return attributes;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setExpectedClientSpanNameMapper(
        HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER);
    optionsBuilder.setClientSpanErrorMapper(
        (uri, exception) -> {
          if (uri.toString().equals("https://192.0.2.1/")) {
            return new ConnectTimeoutException("Connect timeout (PT2S) connecting to " + uri);
          } else if (OS.WINDOWS.isCurrentOs() && uri.toString().equals("http://localhost:61/")) {
            return new ConnectTimeoutException("Connect timeout (PT2S) connecting to " + uri);
          } else if (uri.getPath().equals("/read-timeout")) {
            return new HttpClientReadTimeoutException(
                "Read timeout (PT2S) waiting on HTTP server at " + uri);
          }
          return exception;
        });
  }
}
