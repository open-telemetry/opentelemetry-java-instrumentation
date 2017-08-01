package com.datadoghq.example.restspark;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;

public class MongoDriver {

  public static MongoDatabase getDatabase(final String dbName) {
    final MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
    final MongoClient mongoClient = new MongoClient(connectionString);
    return mongoClient.getDatabase(dbName);
  }
}
