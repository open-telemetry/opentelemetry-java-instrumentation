package com.example.helloworld.api;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.Document;

public class Book {

	private final String title;
	private final int numberPages;
	private final String IsbnCode;


	public Book(String isbnCode, String title, int numberPages) {
		this.title = title;
		this.numberPages = numberPages;
		IsbnCode = isbnCode;
	}

	public Book(Document d) {
		this(d.getString("isbn"), d.getString("title"), d.getInteger("page").intValue());
	}

	@JsonProperty("ISBN")
	public String getIsbnCode() {
		return IsbnCode;
	}


	public String getTitle() {
		return title;
	}

	public int getNumberPages() {
		return numberPages;
	}

	public Document toDocument() {
		return new Document("isbn", IsbnCode)
				.append("title", title)
				.append("page", numberPages);
	}
}
