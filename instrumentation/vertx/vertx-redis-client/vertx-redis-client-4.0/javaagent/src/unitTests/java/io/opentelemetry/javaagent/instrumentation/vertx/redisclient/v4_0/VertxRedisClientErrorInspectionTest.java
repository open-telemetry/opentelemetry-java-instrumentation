/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertxRedisClientErrorInspectionTest {

  @Test
  void responseInspectionFailureIsIgnored() {
    List<Object> response =
        new AbstractList<Object>() {
          @Override
          public Object get(int index) {
            throw new AssertionError();
          }

          @Override
          public int size() {
            return 1;
          }
        };

    assertThat(VertxRedisClientError.findResponseError(response)).isNull();
  }
}
