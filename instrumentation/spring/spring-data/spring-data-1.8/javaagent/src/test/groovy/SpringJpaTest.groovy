/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spring.jpa.JpaCustomer
import spring.jpa.JpaCustomerRepository
import spring.jpa.JpaPersistenceConfig

class SpringJpaTest extends AbstractSpringJpaTest<JpaCustomer, JpaCustomerRepository> {

  JpaCustomer newCustomer(String firstName, String lastName) {
    return new JpaCustomer(firstName, lastName)
  }

  Long id(JpaCustomer customer) {
    return customer.id
  }

  void setFirstName(JpaCustomer customer, String firstName) {
    customer.firstName = firstName
  }

  Class<JpaCustomerRepository> repositoryClass() {
    return JpaCustomerRepository
  }

  JpaCustomerRepository repository() {
    def context = new AnnotationConfigApplicationContext(JpaPersistenceConfig)
    def repo = context.getBean(JpaCustomerRepository)

    // when Spring JPA sets up, it issues metadata queries -- clear those traces
    clearExportedData()

    return repo
  }

  List<JpaCustomer> findByLastName(JpaCustomerRepository repository, String lastName) {
    return repository.findByLastName(lastName)
  }

  List<JpaCustomer> findSpecialCustomers(JpaCustomerRepository repository) {
    return repository.findSpecialCustomers()
  }
}
