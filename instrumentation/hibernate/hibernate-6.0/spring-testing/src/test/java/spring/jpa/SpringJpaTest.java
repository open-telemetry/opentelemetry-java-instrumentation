package spring.jpa;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.Version;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import spring.jpa.Customer;
import spring.jpa.CustomerRepository;
import spring.jpa.PersistenceConfig;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringJpaTest   {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static AnnotationConfigApplicationContext context;
  private static CustomerRepository repo;

  @BeforeAll
  static void setUp() {
    context = new AnnotationConfigApplicationContext(PersistenceConfig.class);
    repo = context.getBean(CustomerRepository.class);
  }

  @AfterAll
  static void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void testCRUD() {
    boolean isHibernate4 = Version.getVersionString().startsWith("4.");
    final Customer customer = new Customer("Bob", "Anonymous");

    assertNull(customer.getId());
    assertFalse(testing.runWithSpan("parent", () -> repo.findAll().iterator().hasNext()));

    AtomicReference<String> sessionId  = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
       span ->
          span.hasName("parent")
              .hasKind(INTERNAL)
              .hasNoParent()
              .hasAttributes(Attributes.empty()),
        span ->
          span.hasName("SELECT spring.jpa.Customer")
              .hasKind(INTERNAL)
              .hasParent(trace.getSpan(0))
              .hasAttributesSatisfyingExactly(
            satisfies(AttributeKey.stringKey("hibernate.session_id"),
                val -> {
                   sessionId.set(String.valueOf(val));
                   val.isInstanceOf(String.class);
                }
             )),
        span ->
            span.hasName("SELECT test.Customer")
                .hasKind(CLIENT)
                    .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM,"hsqldb"),
                            equalTo(maybeStable(DbIncubatingAttributes.DB_NAME),"test"),
                            equalTo(DbIncubatingAttributes.DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(DbIncubatingAttributes.DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            equalTo(maybeStable(DbIncubatingAttributes.DB_STATEMENT), "select ... from Customer ..."),
                            equalTo(maybeStable(DbIncubatingAttributes.DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DbIncubatingAttributes.DB_SQL_TABLE), "Customer")
                        ),
        span -> span.hasName("Transaction.commit")
                .hasKind(INTERNAL)
                    .hasParent(trace.getSpan(0))
            .hasAttributesSatisfyingExactly(
                equalTo(AttributeKey.stringKey("hibernate.session_id"),sessionId.get())
            )
        ));

    testing.clearData();

    testing.runWithSpan("parent", () -> repo.save(customer));
    Long savedId = customer.getId();

    assertNotNull(customer.getId());
    String sessionId2 = null;
    // todo assert
    testing.clearData();


    customer.setFirstName("Bill");
    testing.runWithSpan("parent", () -> repo.save(customer));

    assertEquals(customer.getId(), savedId);
    String sessionId3 = null;
    // todo assert
    testing.clearData();
    Customer customer1 = testing.runWithSpan("parent",
        () -> repo.findByLastName("Anonymous").get(0));


    assertEquals(savedId, customer1.getId() );
    assertEquals("Bill", customer1.getFirstName());
    // todo assert
    testing.clearData();

    testing.runWithSpan("parent", () -> repo.delete(customer1));

    //todo assert
  }
}
