/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.emptyMap;

import com.couchbase.client.core.CoreProtostellar;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.PasswordAuthenticator;
import com.couchbase.client.core.protostellar.ProtostellarRequest;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class CouchbaseProtostellar344Test {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void capturesTargetWithOriginalConstructor() {
    CoreEnvironment environment = CoreEnvironment.create();
    try {
      CoreProtostellar core =
          new CoreProtostellar(
              environment,
              PasswordAuthenticator.create("user", "password"),
              ConnectionString.create("protostellar://node:18099"));
      try {
        RequestSpan requestSpan = environment.requestTracer().requestSpan("get", null);
        ProtostellarRequest<Object> request =
            new ProtostellarRequest<>(
                core,
                ServiceType.KV,
                "get",
                requestSpan,
                Duration.ofSeconds(1),
                true,
                environment.retryStrategy(),
                emptyMap());
        request.raisedResponseToUser(null);

        testing.waitAndAssertTracesWithoutScopeVersionVerification(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("get node:18099")
                            .hasKind(CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(SERVER_ADDRESS, "node"), equalTo(SERVER_PORT, 18099L))));
      } finally {
        core.shutdown(Duration.ofSeconds(10)).block();
      }
    } finally {
      environment.close();
    }
  }
}
