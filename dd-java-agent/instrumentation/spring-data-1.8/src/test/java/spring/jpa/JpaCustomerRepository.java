// This file includes software developed at SignalFx

package spring.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCustomerRepository extends JpaRepository<JpaCustomer, Long> {
  List<JpaCustomer> findByLastName(String lastName);
}
