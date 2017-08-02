package com.example.helloworld.resources;

import com.datadoghq.trace.Trace;
import com.example.helloworld.api.Book;
import com.google.common.base.Optional;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import io.opentracing.ActiveSpan;
import io.opentracing.util.GlobalTracer;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.bson.Document;

@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class SimpleCrudResource {

  private final MongoClient client;
  private final MongoDatabase db;
  private static final String HOSTNAME = "localhost";
  private static final String DATABASE = "demo";
  private static final java.lang.String COLLECTION = "books";

  public SimpleCrudResource() {

    // Init the client
    client = new MongoClient(HOSTNAME);

    // For this example, start from a fresh DB
    try {
      client.dropDatabase(DATABASE);
    } catch (Exception e) {
      // do nothing here
    }

    // Init the connection to the collection
    db = client.getDatabase(DATABASE);
    db.createCollection(COLLECTION);
  }

  /**
   * Add a book to the DB
   *
   * @return The status of the save
   * @throws InterruptedException
   */
  @GET
  @Path("/add")
  public String addBook(
      @QueryParam("isbn") Optional<String> isbn,
      @QueryParam("title") Optional<String> title,
      @QueryParam("page") Optional<Integer> page)
      throws InterruptedException {

    // The methodDB is traced (see below), this will be produced a new child span
    beforeDB();

    if (!isbn.isPresent()) {
      throw new IllegalArgumentException("ISBN should not be null");
    }

    Book book = new Book(isbn.get(), title.or("Missing title"), page.or(0));

    db.getCollection(COLLECTION).insertOne(book.toDocument());
    return "Book saved!";
  }

  /**
   * List all books present in the DB
   *
   * @return list of Books
   * @throws InterruptedException
   */
  @GET
  public List<Book> getBooks() throws InterruptedException {

    // The methodDB is traced (see below), this will be produced a new childre span
    beforeDB();

    List<Book> books = new ArrayList<>();
    try (MongoCursor<Document> cursor = db.getCollection(COLLECTION).find().iterator(); ) {
      while (cursor.hasNext()) {
        books.add(new Book(cursor.next()));
      }
    }

    // The methodDB is traced (see below), this will be produced a new child span
    afterDB();

    return books;
  }

  /**
   * The beforeDB is traced using the annotation @Trace with a custom operationName and a custom
   * tag.
   *
   * @throws InterruptedException
   */
  @Trace(operationName = "database.before")
  public void beforeDB() throws InterruptedException {
    ActiveSpan currentSpan = GlobalTracer.get().activeSpan();
    if (currentSpan != null) {
      currentSpan.setTag("status", "started");
      Thread.sleep(10);
    }
  }

  /**
   * The afterDB is traced using the annotation @Trace with a custom operationName and a custom tag.
   *
   * @throws InterruptedException
   */
  @Trace(operationName = "database.after")
  public void afterDB() throws InterruptedException {
    ActiveSpan currentSpan = GlobalTracer.get().activeSpan();
    if (currentSpan != null) {
      currentSpan.setTag("status", "started");
      Thread.sleep(10);
    }
  }

  /** Flush resources */
  public void close() {
    client.close();
  }
}
