/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data.v1_8;

import io.opentelemetry.javaagent.instrumentation.spring.data.AbstractSpringJpaTest;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import spring.jpa.JpaCustomer;
import spring.jpa.JpaCustomerRepository;
import spring.jpa.JpaPersistenceConfig;

public class SpringJpaTest extends AbstractSpringJpaTest<JpaCustomer, JpaCustomerRepository> {

  @Override
  protected JpaCustomer newCustomer(String firstName, String lastName) {
    return new JpaCustomer(firstName, lastName);
  }

  @Override
  protected Long id(JpaCustomer customer) {
    return customer.getId();
  }

  @Override
  protected void setFirstName(JpaCustomer customer, String firstName) {
    customer.setFirstName(firstName);
  }

  @Override
  protected Class<JpaCustomerRepository> repositoryClass() {
    return JpaCustomerRepository.class;
  }

  @Override
  protected JpaCustomerRepository repository() {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(JpaPersistenceConfig.class);
    JpaCustomerRepository repo = context.getBean(JpaCustomerRepository.class);

    // when Spring JPA sets up, it issues metadata queries -- clear those traces
    clearData();

    return repo;
  }

  @Override
  protected List<JpaCustomer> findByLastName(JpaCustomerRepository repository, String lastName) {
    return repository.findByLastName(lastName);
  }

  @Override
  protected List<JpaCustomer> findSpecialCustomers(JpaCustomerRepository repository) {
    return repository.findSpecialCustomers();
  }

  @Override
  protected Optional<JpaCustomer> findOneByLastName(
      JpaCustomerRepository repository, String lastName) {
    return repository.findOneByLastName(lastName);
  }
}
