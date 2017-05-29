package com.example.helloworld.resources;

import com.example.helloworld.api.Book;
import com.google.common.base.Optional;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import io.opentracing.contrib.agent.Trace;
import org.bson.Document;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class SimpleCrudResource {

	// Instantiate Synchronous Tracing MongoClient
	private final MongoDatabase db;

	public SimpleCrudResource() {

		MongoClientOptions settings = MongoClientOptions.builder()
				.codecRegistry(com.mongodb.MongoClient.getDefaultCodecRegistry())
				.build();

		MongoClient client = new MongoClient("localhost", settings);

		client.dropDatabase("demo");

		db = client.getDatabase("demo");
		db.createCollection("books");


	}


	@GET
	@Path("/add")
	public String addBook(
			@QueryParam("isbn") Optional<String> isbn,
			@QueryParam("title") Optional<String> title,
			@QueryParam("page") Optional<Integer> page
	) throws InterruptedException {


		// Simple business need to execute before saving a new book
		beforeDB();

		if (!isbn.isPresent()) {
			throw new IllegalArgumentException("ISBN should not be null");
		}

		Book book = new Book(
				isbn.get(),
				title.or("missing title"),
				page.or(0));

		db.getCollection("books").insertOne(book.toDocument());
		return "Book saved";
	}

	@GET
	public List<Book> getBooks() throws InterruptedException {

		// Simple business need to execute before saving a new book
		beforeDB();

		List<Book> books = new ArrayList<>();
		try (MongoCursor<Document> cursor = db.getCollection("books").find().iterator();) {
			while (cursor.hasNext()) {
				books.add(new Book(cursor.next()));
			}
		}

		// Simple business need to execute after retrieve the book list
		afterDB();

		return books;
	}

	@Trace(operationName = "Before DB", tagsKV = {"mytag", "myvalue"})
	public void beforeDB() throws InterruptedException {
		Thread.sleep(333);
	}

	@Trace(operationName = "After DB", tagsKV = {"mytag", "myvalue"})
	public void afterDB() throws InterruptedException {
		Thread.sleep(111);
	}
}

