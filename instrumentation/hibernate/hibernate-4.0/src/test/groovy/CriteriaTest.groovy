/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.instrumentation.api.Tags
import org.hibernate.Criteria
import org.hibernate.Session
import org.hibernate.criterion.Order
import org.hibernate.criterion.Restrictions

import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.INTERNAL

class CriteriaTest extends AbstractHibernateTest {

  def "test criteria.#methodName"() {
    setup:
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    Criteria criteria = session.createCriteria(Value)
      .add(Restrictions.like("name", "Hello"))
      .addOrder(Order.desc("name"))
    interaction.call(criteria)
    session.getTransaction().commit()
    session.close()

    expect:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "Session"
          spanKind INTERNAL
          parent()
          tags {
          }
        }
        span(1) {
          operationName "Criteria.$methodName"
          spanKind INTERNAL
          childOf span(0)
          tags {
          }
        }
        span(2) {
          operationName ~/^select /
          spanKind CLIENT
          childOf span(1)
          tags {
            "$Tags.DB_TYPE" "sql"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "$Tags.DB_URL" "h2:mem:"
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(3) {
          operationName "Transaction.commit"
          spanKind INTERNAL
          childOf span(0)
          tags {
          }
        }
      }
    }

    where:
    methodName     | interaction
    "list"         | { c -> c.list() }
    "uniqueResult" | { c -> c.uniqueResult() }
    "scroll"       | { c -> c.scroll() }
  }
}
