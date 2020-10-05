/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springdata

import org.springframework.data.couchbase.repository.CouchbaseRepository

interface DocRepository extends CouchbaseRepository<Doc, String> {}
