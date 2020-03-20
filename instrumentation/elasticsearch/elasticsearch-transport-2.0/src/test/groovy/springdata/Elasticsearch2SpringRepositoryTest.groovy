/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package springdata

import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class Elasticsearch2SpringRepositoryTest extends AgentTestRunner {
  @Shared
  ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config)

  @Shared
  DocRepository repo = applicationContext.getBean(DocRepository)

  def setup() {
    TEST_WRITER.clear()
    runUnderTrace("delete") {
      repo.deleteAll()
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()
  }

  def "test empty repo"() {
    when:
    def result = repo.findAll()

    then:
    !result.iterator().hasNext()

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "SearchAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }

  def "test CRUD"() {
    when:
    def doc = new Doc()

    then:
    repo.index(doc) == doc

    and:
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "IndexAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$MoreTags.NET_PEER_NAME" "local"
            "$MoreTags.NET_PEER_IP" "0.0.0.0"
            "$MoreTags.NET_PEER_PORT" 0
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
          }
        }
        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "PutMappingAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
            "elasticsearch.request.indices" indexName
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
    }
    TEST_WRITER.clear()

    and:
    repo.findOne("1") == doc

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "GetAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$MoreTags.NET_PEER_NAME" "local"
            "$MoreTags.NET_PEER_IP" "0.0.0.0"
            "$MoreTags.NET_PEER_PORT" 0
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    doc.data = "other data"

    then:
    repo.index(doc) == doc
    repo.findOne("1") == doc

    and:
    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "IndexAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$MoreTags.NET_PEER_NAME" "local"
            "$MoreTags.NET_PEER_IP" "0.0.0.0"
            "$MoreTags.NET_PEER_PORT" 0
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "GetAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$MoreTags.NET_PEER_NAME" "local"
            "$MoreTags.NET_PEER_IP" "0.0.0.0"
            "$MoreTags.NET_PEER_PORT" 0
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.delete("1")

    then:
    !repo.findAll().iterator().hasNext()

    and:
    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "DeleteAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$MoreTags.NET_PEER_NAME" "local"
            "$MoreTags.NET_PEER_IP" "0.0.0.0"
            "$MoreTags.NET_PEER_PORT" 0
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "DeleteAction"
            "elasticsearch.request" "DeleteRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "SearchAction"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }
}
