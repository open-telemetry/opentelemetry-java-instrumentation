/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.health.ClusterHealthStatus

abstract class AbstractElasticsearchNodeClientTest extends AbstractElasticsearchClientTest {

  abstract Client client()

  ClusterHealthStatus clusterHealthSync() {
    def result = client().admin().cluster().health(new ClusterHealthRequest())
    return runWithSpan("callback") {
      result.get().status
    }
  }

  ClusterHealthStatus clusterHealthAsync() {
    def result = new Result<ClusterHealthResponse>()
    client().admin().cluster().health(new ClusterHealthRequest(), new ResultListener<ClusterHealthResponse>(this, result))
    return result.get().status
  }

  def prepareGetSync(indexName, indexType, id) {
    try {
      client().prepareGet(indexName, indexType, id).get()
    } finally {
      runWithSpan("callback") {}
    }
  }

  def prepareGetAsync(indexName, indexType, id) {
    def result = new Result<GetResponse>()
    client().prepareGet(indexName, indexType, id).execute(new ResultListener<GetResponse>(this, result))
    result.get()
  }
}
