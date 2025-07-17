plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:r2dbc-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-data:spring-data-1.8:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-data:spring-data-common:testing"))

  testLibrary("org.hibernate.orm:hibernate-core:6.0.0.Final")
  testLibrary("org.springframework.data:spring-data-commons:3.0.0")
  testLibrary("org.springframework.data:spring-data-jpa:3.0.0")
  testLibrary("org.springframework.data:spring-data-r2dbc:3.0.0")
  testLibrary("org.springframework:spring-test:6.0.0")

  testImplementation("org.hsqldb:hsqldb:2.0.0")
  testImplementation("com.h2database:h2:1.4.197")
  testImplementation("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

testing {
  suites {
    val reactiveTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.springframework.data:spring-data-r2dbc:3.0.0")
        implementation("org.testcontainers:testcontainers")
        implementation("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
        implementation("com.h2database:h2:1.4.197")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testing.suites)
    dependsOn(testStableSemconv)
  }
}
