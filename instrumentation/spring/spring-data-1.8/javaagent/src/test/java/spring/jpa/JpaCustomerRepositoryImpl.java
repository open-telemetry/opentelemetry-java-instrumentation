/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.jpa;

import java.util.List;
import javax.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;

public class JpaCustomerRepositoryImpl implements JpaCustomerRepositoryCustom {
  @Autowired private EntityManager entityManager;

  @Override
  public List<JpaCustomer> findSpecialCustomers() {
    return entityManager.createQuery("from JpaCustomer", JpaCustomer.class).getResultList();
  }
}
