/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase.springdata;

import org.springframework.data.couchbase.repository.CouchbaseRepository;

public interface TestRepository extends CouchbaseRepository<TestDocument, String> {}
