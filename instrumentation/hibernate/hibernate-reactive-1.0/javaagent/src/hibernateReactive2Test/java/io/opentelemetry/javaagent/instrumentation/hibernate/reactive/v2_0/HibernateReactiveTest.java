/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v2_0;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.concurrent.TimeUnit;

class HibernateReactiveTest extends AbstractHibernateReactiveTest {

  @Override
  protected EntityManagerFactory createEntityManagerFactory() throws Exception {
    return vertx
        .getOrCreateContext()
        .<EntityManagerFactory>executeBlocking(
            promise -> promise.complete(Persistence.createEntityManagerFactory("test-pu")))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, TimeUnit.SECONDS);
  }
}
