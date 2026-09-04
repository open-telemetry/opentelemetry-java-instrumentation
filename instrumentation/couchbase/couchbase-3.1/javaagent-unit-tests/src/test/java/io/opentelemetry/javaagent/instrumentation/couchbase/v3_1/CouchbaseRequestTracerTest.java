/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import com.couchbase.client.core.msg.RequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTargets;
import org.junit.jupiter.api.Test;

class CouchbaseRequestTracerTest {

  @Test
  void settingConfiguredPortDoesNotThrowWithoutRequestSpanLongOverload() {
    RequestTracer tracer =
        CouchbaseRequestTracer.create(OpenTelemetry.noop().getTracer("test-couchbase"));
    RequestSpan span = tracer.requestSpan("test", null);
    CouchbaseServerTarget target =
        CouchbaseServerTarget.direct(
            DbServerTarget.builder(11210).addEndpoint("db.example", 11210).build());
    assertThat(target).isNotNull();

    Core core = mock(Core.class);
    RequestContext requestContext = mock(RequestContext.class);
    when(requestContext.core()).thenReturn(core);
    CouchbaseServerTargets.register(core, target);

    span.requestContext(requestContext);
  }
}
