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

package io.opentelemetry.auto.test;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class AgentClassRule extends AgentTestRunner implements TestRule {

  private static final ThreadLocal<AgentClassRule> CLASSRULES = new ThreadLocal<>();

  public InMemoryExporter getTestWriter() {
    return TEST_WRITER;
  }

  public static class AgentRule implements TestRule {
    @Override
    public Statement apply(final Statement base, Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          AgentClassRule classRule = CLASSRULES.get();
          if (classRule != null) {
            classRule.beforeTest();
          }
          base.evaluate();
        }
      };
    }
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    final AgentClassRule classRule = this;
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        agentSetup();
        setupBeforeTests();
        CLASSRULES.set(classRule);
        try {
          base.evaluate();
        } finally{
          CLASSRULES.remove();
          cleanUpAfterTests();
          agentCleanup();
        }
      }
    };
  }
}
