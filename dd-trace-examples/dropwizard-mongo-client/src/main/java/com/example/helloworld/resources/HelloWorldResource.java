package com.example.helloworld.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.bson.Document;

import com.codahale.metrics.annotation.Timed;
import com.example.helloworld.api.Saying;
import com.google.common.base.Optional;
import com.mongodb.MongoClient;
import com.mongodb.client.ListDatabasesIterable;

import io.opentracing.contrib.agent.Trace;

@Path("/hello")
@Produces(MediaType.APPLICATION_JSON)
public class HelloWorldResource {
	private final String template;
	private final String defaultName;
	private final AtomicLong counter;

	// Instantiate Synchronous Tracing MongoClient
	private final MongoClient mongoClient = new MongoClient("localhost", 27017);

	public HelloWorldResource(String template, String defaultName) {
		this.template = template;
		this.defaultName = defaultName;
		this.counter = new AtomicLong();
	}

	@GET
	@Timed
	public Saying sayHello(@QueryParam("name") Optional<String> name) throws InterruptedException {
		final String value = String.format(template, name.or(defaultName));
		return new Saying(counter.incrementAndGet(), value);
	}

	@GET
	@Path("/history")
	public Object history() throws InterruptedException {
		beforeDB();

		// Trace: Do  some stuff with the DB
		ListDatabasesIterable<Document> documents = mongoClient.listDatabases();
		final List<String> list = new ArrayList<String>();
		documents.forEach(new Consumer<Document>() {
			@Override
			public void accept(Document t) {
				list.add(t.toJson());
			}
		});
		
		afterDB();

		return list;
	}

	@Trace(operationName="Before DB",tagsKV={"service-name","method"})
	public void beforeDB() throws InterruptedException{
		Thread.sleep(333);
	}

	@Trace(operationName="After DB",tagsKV={"service-name","method"})
	public void afterDB() throws InterruptedException{
		 Thread.sleep(111);
	}

}