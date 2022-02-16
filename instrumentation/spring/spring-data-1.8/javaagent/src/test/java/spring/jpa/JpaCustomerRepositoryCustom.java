/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.jpa;

import java.util.List;

public interface JpaCustomerRepositoryCustom {
  List<JpaCustomer> findSpecialCustomers();
}
