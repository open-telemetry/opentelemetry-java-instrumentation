// This file includes software developed at SignalFx

package spring.jpa;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface JpaCustomerRepository extends CrudRepository<JpaCustomer, Long> {

  List<JpaCustomer> findByLastName(String lastName);
}
