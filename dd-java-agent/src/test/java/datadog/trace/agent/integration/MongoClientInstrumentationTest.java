package datadog.trace.agent.integration;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTracer;
import datadog.trace.agent.test.IntegrationTestUtils;
import datadog.trace.common.writer.ListWriter;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.opentracing.tag.Tags;
import java.util.concurrent.TimeoutException;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class MongoClientInstrumentationTest {
  public static final String MONGO_DB_NAME = "embedded";
  public static final String MONGO_HOST = "localhost";
  public static final int MONGO_PORT = 12345;
  private static MongodExecutable mongodExe;
  private static MongodProcess mongod;

  private static MongoClient client;
  private static final ListWriter writer = new ListWriter();
  private static final DDTracer tracer = new DDTracer(writer);

  public static void startLocalMongo() throws Exception {
    final MongodStarter starter = MongodStarter.getDefaultInstance();
    final IMongodConfig mongodConfig =
        new MongodConfigBuilder()
            .version(Version.Main.PRODUCTION)
            .net(new Net(MONGO_HOST, MONGO_PORT, Network.localhostIsIPv6()))
            .build();

    mongodExe = starter.prepare(mongodConfig);
    mongod = mongodExe.start();
  }

  public static void stopLocalMongo() throws Exception {
    if (null != mongod) {
      mongod.stop();
      mongod = null;
    }
    if (null != mongodExe) {
      mongodExe.stop();
      mongodExe = null;
    }
  }

  @BeforeClass
  public static void setup() throws Exception {
    IntegrationTestUtils.registerOrReplaceGlobalTracer(tracer);
    startLocalMongo();
    // Embeded Mongo uses HttpUrlConnection to download things so we have to clear traces before
    // going to tests
    writer.clear();

    client = new MongoClient(MONGO_HOST, MONGO_PORT);
  }

  @AfterClass
  public static void destroy() throws Exception {
    if (null != client) {
      client.close();
      client = null;
    }
    stopLocalMongo();
  }

  @Test
  public void syncClientHasListener() {
    Assert.assertEquals(1, client.getMongoClientOptions().getCommandListeners().size());
    Assert.assertEquals(
        "TracingCommandListener",
        client.getMongoClientOptions().getCommandListeners().get(0).getClass().getSimpleName());
  }

  @Test
  public void insertOperation() throws TimeoutException, InterruptedException {
    final MongoDatabase db = client.getDatabase(MONGO_DB_NAME);
    final String collectionName = "testCollection";
    db.createCollection(collectionName);
    final MongoCollection<Document> collection = db.getCollection(collectionName);

    collection.insertOne(new Document("foo", "bar"));

    Assert.assertEquals(1, collection.count());

    Assert.assertEquals(3, writer.size());

    final String createCollectionQuery =
        "{ \"create\" : \"testCollection\", \"autoIndexId\" : \"?\", \"capped\" : \"?\" }";
    final DDSpan trace0 = writer.get(0).get(0);
    Assert.assertEquals("mongo.query", trace0.getOperationName());
    Assert.assertEquals(createCollectionQuery, trace0.getResourceName());
    Assert.assertEquals("mongodb", trace0.getType());
    Assert.assertEquals("mongo", trace0.getServiceName());

    Assert.assertEquals("java-mongo", trace0.getTags().get(Tags.COMPONENT.getKey()));
    Assert.assertEquals(createCollectionQuery, trace0.getTags().get(Tags.DB_STATEMENT.getKey()));
    Assert.assertEquals(MONGO_DB_NAME, trace0.getTags().get(Tags.DB_INSTANCE.getKey()));
    Assert.assertEquals(MONGO_HOST, trace0.getTags().get(Tags.PEER_HOSTNAME.getKey()));
    Assert.assertEquals("127.0.0.1", trace0.getTags().get(Tags.PEER_HOST_IPV4.getKey()));
    Assert.assertEquals(MONGO_PORT, trace0.getTags().get(Tags.PEER_PORT.getKey()));
    Assert.assertEquals("mongo", trace0.getTags().get(Tags.DB_TYPE.getKey()));
  }
}
