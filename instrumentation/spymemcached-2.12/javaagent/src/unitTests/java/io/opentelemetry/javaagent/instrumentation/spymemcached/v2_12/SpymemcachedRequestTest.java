/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.spy.memcached.MemcachedConnection;
import org.junit.jupiter.api.Test;

class SpymemcachedRequestTest {

  @Test
  void queryArgumentsAreCapturedWithoutChangingOperationName() {
    List<String> keys = new ArrayList<>(asList("key1", "key2"));
    SpymemcachedRequest request =
        SpymemcachedRequest.create(
            mock(MemcachedConnection.class), "asyncGetBulk", "", new Object[] {keys});

    keys.clear();

    assertThat(request.getOperationName()).isEqualTo("getBulk");
    assertThat(request.getStableOperationName()).isEqualTo("get");
    assertThat(new SpymemcachedAttributesGetter().getDbQueryText(request))
        .isEqualTo("getBulk key1 key2");
  }

  @Test
  void synchronousBulkSnapshotIsUsedOnceOnlyForBulkRequests() {
    MemcachedConnection connection = mock(MemcachedConnection.class);
    try (Scope scope =
        Context.root()
            .with(SpymemcachedRequest.BULK_QUERY_TEXT, new AtomicReference<>("getBulk key1 key2"))
            .makeCurrent()) {
      assertThat(
              SpymemcachedRequest.create(
                      connection,
                      "asyncGetBulk",
                      "",
                      new Object[] {asList("key1", "key2").iterator()})
                  .getQueryText())
          .isEqualTo("getBulk key1 key2");
      assertThat(
              SpymemcachedRequest.create(
                      connection, "asyncGetBulk", "", new Object[] {asList("key3", "key4")})
                  .getQueryText())
          .isEqualTo("getBulk key3 key4");
      assertThat(
              SpymemcachedRequest.create(connection, "asyncGet", "", new Object[] {"key3"})
                  .getQueryText())
          .isEqualTo("get key3");
    }
  }
}
