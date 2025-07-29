/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

interface DocRepository extends ElasticsearchRepository<Doc, String> {}
