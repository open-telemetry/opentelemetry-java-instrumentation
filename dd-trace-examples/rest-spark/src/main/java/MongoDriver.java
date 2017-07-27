import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import io.opentracing.Tracer;
import io.opentracing.contrib.mongo.TracingMongoClient;

public class MongoDriver {

  public static MongoDatabase getDatabase(String dbName) {
    Tracer tracer = io.opentracing.util.GlobalTracer.get();
    MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
    MongoClient mongoClient = new TracingMongoClient(tracer, connectionString);
    return mongoClient.getDatabase(dbName);
  }
}
