package io.opentelemetry.spring.smoketest;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerRepository extends MongoRepository<Customer, String> {
	Customer findByFirstName(String firstName);
}
