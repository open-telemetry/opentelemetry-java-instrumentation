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

import io.opentelemetry.auto.test.AgentTestRunner
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import spock.lang.Shared

abstract class AbstractHibernateTest extends AgentTestRunner {

  @Shared
  protected SessionFactory sessionFactory

  @Shared
  protected List<Value> prepopulated

  def setupSpec() {
    sessionFactory = new Configuration().configure().buildSessionFactory()
    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession()
    writer.beginTransaction()
    prepopulated = new ArrayList<>()
    for (int i = 0; i < 2; i++) {
      prepopulated.add(new Value("Hello :) " + i))
      writer.save(prepopulated.get(i))
    }
    writer.getTransaction().commit()
    writer.close()
  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }
}
