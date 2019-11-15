import datadog.trace.api.Trace;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NamedQuery;

@Entity
@Table
@NamedQuery(name = "TestNamedQuery", query = "from Value")
public class Value {

  private Long id;
  private String name;

  public Value() {}

  public Value(final String name) {
    this.name = name;
  }

  @Id
  @GeneratedValue(generator = "increment")
  @GenericGenerator(name = "increment", strategy = "increment")
  public Long getId() {
    return id;
  }

  private void setId(final Long id) {
    this.id = id;
  }

  @Trace
  public String getName() {
    return name;
  }

  public void setName(final String title) {
    this.name = title;
  }
}
