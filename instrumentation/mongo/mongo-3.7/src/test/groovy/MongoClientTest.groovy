/*
 * Copyright The OpenTelemetry Authors
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


import com.mongodb.MongoClientSettings
import com.mongodb.MongoTimeoutException
import com.mongodb.ServerAddress
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class MongoClientTest extends MongoBaseTest {

  @Shared
  MongoClient client

  def setup() throws Exception {
    client = MongoClients.create(MongoClientSettings.builder()
      .applyToClusterSettings({ builder ->
        builder.hosts(Arrays.asList(
          new ServerAddress("localhost", port)))
          .description("some-description")
      })
      .build())
  }

  def cleanup() throws Exception {
    client?.close()
    client = null
  }

  def "test create collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    db.createCollection(collectionName)

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "{\"create\":\"$collectionName\",\"capped\":\"?\"}")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  // Tests the fix for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/457
  // TracingCommandListener might get added multiple times if ClientSettings are built using existing ClientSettings or when calling  a build method twice.
  // This test asserts that duplicate traces are not created in those cases.
  def "test create collection with already built ClientSettings"() {
    setup:
    def clientSettings = MongoClientSettings.builder()
      .applyToClusterSettings({ builder ->
        builder.hosts(Arrays.asList(
          new ServerAddress("localhost", port)))
          .description("some-description")
      })
      .build()
    def newClientSettings = MongoClientSettings.builder(clientSettings).build()
    MongoDatabase db = MongoClients.create(newClientSettings).getDatabase(dbName)

    when:
    db.createCollection(collectionName)

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "{\"create\":\"$collectionName\",\"capped\":\"?\"}")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test create collection no description"() {
    setup:
    MongoDatabase db = MongoClients.create("mongodb://localhost:" + port).getDatabase(dbName)

    when:
    db.createCollection(collectionName)

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "{\"create\":\"$collectionName\",\"capped\":\"?\"}", dbName)
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test get collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    int count = db.getCollection(collectionName).count()

    then:
    count == 0
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test insert"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    collection.insertOne(new Document("password", "SECRET"))

    then:
    collection.count() == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "{\"insert\":\"$collectionName\",\"ordered\":\"?\",\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}")
      }
      trace(1, 1) {
        mongoSpan(it, 0, "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test update"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "OLDPW"))
      return coll
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    def result = collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW"))))

    then:
    result.modifiedCount == 1
    collection.count() == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "{\"update\":\"?\",\"ordered\":\"?\",\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"\$set\":{\"password\":\"?\"}}}]}")
      }
      trace(1, 1) {
        mongoSpan(it, 0, "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test delete"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "SECRET"))
      return coll
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    def result = collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")))

    then:
    result.deletedCount == 1
    collection.count() == 0
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "{\"delete\":\"?\",\"ordered\":\"?\",\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}")
      }
      trace(1, 1) {
        mongoSpan(it, 0, "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test error"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    collection.updateOne(new BsonDocument(), new BsonDocument())

    then:
    thrown(IllegalArgumentException)
    // Unfortunately not caught by our instrumentation.
    assertTraces(0) {}

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test client failure"() {
    setup:
    def client = MongoClients.create("mongodb://localhost:" + UNUSABLE_PORT + "/?connectTimeoutMS=10")

    when:
    MongoDatabase db = client.getDatabase(dbName)
    db.createCollection(collectionName)

    then:
    thrown(MongoTimeoutException)
    // Unfortunately not caught by our instrumentation.
    assertTraces(0) {}

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def mongoSpan(TraceAssert trace, int index, String statement, String instance = "some-description", Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      operationName { it.replace(" ", "") == statement }
      spanKind CLIENT
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {
        "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
        "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
        "${SemanticAttributes.NET_PEER_PORT.key()}" port
        "${SemanticAttributes.DB_STATEMENT.key()}" {
          it.replace(" ", "") == statement
        }
        "${SemanticAttributes.DB_SYSTEM.key()}" "mongodb"
        "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "mongodb://localhost:" + port
        "${SemanticAttributes.DB_NAME.key()}" instance
      }
    }
  }
}
