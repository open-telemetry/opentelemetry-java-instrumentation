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
import static java.util.Collections.singleton;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.PasswordAuthenticator;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.protostellar.ProtostellarRequest;
import com.couchbase.client.core.service.ServiceType;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class CouchbaseProtostellar343Test {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void capturesTargetWithLegacyConstructors() {
    CoreEnvironment environment = CoreEnvironment.create();
    try {
      Core core =
          Core.create(
              environment,
              PasswordAuthenticator.create("user", "password"),
              singleton(SeedNode.create("node").withProtostellarPort(18099)));
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
        core.close();
      }
    } finally {
      environment.close();
    }
  }
}
