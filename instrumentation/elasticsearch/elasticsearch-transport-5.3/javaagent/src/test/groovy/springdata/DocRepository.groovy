/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springdata

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface DocRepository extends ElasticsearchRepository<Doc, String> {}
