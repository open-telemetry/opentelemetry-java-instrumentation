import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:jdbc:bootstrap"))
  implementation(project(":instrumentation:jdbc:javaagent-common"))
  compileOnly(
    project(
      path = ":instrumentation:jdbc:library",
      configuration = "shadow",
    ),
  )
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("com.h2database:h2:1.3.169")
  testLibrary("org.apache.derby:derby:10.6.1.0")
  testLibrary("org.hsqldb:hsqldb:2.0.0")
  testLibrary("org.xerial:sqlite-jdbc:3.53.1.0")
  testLibrary("com.mysql:mysql-connector-j:9.3.0")

  testLibrary("org.apache.tomcat:tomcat-jdbc:7.0.19")
  testLibrary("org.apache.tomcat:tomcat-juli:7.0.19") // tomcat jdbc needs this
  testLibrary("com.zaxxer:HikariCP:2.4.0")
  testLibrary("com.mchange:c3p0:0.9.5")
  testLibrary("com.alibaba:druid:1.2.20")

  // some classes in earlier versions of derby were split out into derbytools in later versions
  latestDepTestLibrary("org.apache.derby:derbytools:latest.release")

  testImplementation(project(":instrumentation:jdbc:testing"))

  // these dependencies are for SlickTest
  testImplementation("org.scala-lang:scala-library:2.11.12")
  testImplementation("com.typesafe.slick:slick_2.11:3.2.0")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }
}

testing.suites {
  executions("test") {
    configureDefault {
      filter {
        excludeTestsMatching("SlickTest")
        excludeTestsMatching("SqlCommenterTest")
        excludeTestsMatching("PreparedStatementParametersTest")
        excludeTestsMatching("*JdbcInstrumentationTest.testCommitTransaction")
        excludeTestsMatching("*JdbcInstrumentationTest.testRollbackTransaction")
        excludeTestsMatching("*JdbcInstrumentationTest.testFailedTransaction")
      }
      jvmArgs("-Dotel.instrumentation.jdbc-datasource.enabled=true")
    }

    register("Slick") {
      filter {
        includeTestsMatching("SlickTest")
      }
      include("**/SlickTest.*")
    }

    register("SqlCommenter") {
      filter {
        includeTestsMatching("SqlCommenterTest")
      }
      include("**/SqlCommenterTest.*")
      // This property is read in TestAgentSqlCommenterCustomizer, we use it instead of the
      // otel.instrumentation.jdbc.experimental.sqlcommenter.enabled to test that the
      // SqlCommenterCustomizer is run.
      jvmArgs("-Dotel.testing.sqlcommenter.enabled=true")
    }

    register("CaptureParameters") {
      filter {
        includeTestsMatching("PreparedStatementParametersTest")
      }
      telemetryConfig("otel.instrumentation.jdbc.experimental.capture-query-parameters", "true")
    }

    register("ExceptionSignalLogs") {
      filter {
        excludeTestsMatching("SlickTest")
        excludeTestsMatching("SqlCommenterTest")
        excludeTestsMatching("PreparedStatementParametersTest")
        excludeTestsMatching("*JdbcInstrumentationTest.testCommitTransaction")
        excludeTestsMatching("*JdbcInstrumentationTest.testRollbackTransaction")
        excludeTestsMatching("*JdbcInstrumentationTest.testFailedTransaction")
      }
      jvmArgs("-Dotel.instrumentation.jdbc-datasource.enabled=true")
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
      telemetryConfig("otel.semconv.exception.signal.preview", "logs")
    }

    register("Transactions") {
      filter {
        includeTestsMatching("*JdbcInstrumentationTest.testCommitTransaction")
        includeTestsMatching("*JdbcInstrumentationTest.testRollbackTransaction")
        includeTestsMatching("*JdbcInstrumentationTest.testFailedTransaction")
      }
      telemetryConfig("otel.instrumentation.jdbc.experimental.transaction.enabled", "true")
    }
  }
}
