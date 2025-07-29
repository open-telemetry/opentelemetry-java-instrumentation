plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  // We have two independent covariants, so we have to test them independently.
  pass {
    group.set("org.springframework.data")
    module.set("spring-data-commons")
    versions.set("[1.8.0.RELEASE,]")
    extraDependency("org.springframework:spring-aop:1.2")
    assertInverse.set(true)
  }
  pass {
    group.set("org.springframework")
    module.set("spring-aop")
    versions.set("[1.2,]")
    extraDependency("org.springframework.data:spring-data-commons:1.8.0.RELEASE")
    assertInverse.set(true)
  }
}

// DQH - API changes that impact instrumentation occurred in spring-data-commons in March 2014.
// For now, that limits support to spring-data-commons 1.9.0 (maybe 1.8.0).
// For testing, chose a couple spring-data modules that are old enough to work with 1.9.0.
dependencies {
  library("org.springframework.data:spring-data-commons:1.8.0.RELEASE")
  compileOnly("org.springframework:spring-aop:1.2")
  compileOnly(project(":instrumentation-annotations-support"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-data:spring-data-common:testing"))

  testLibrary("org.hibernate:hibernate-entitymanager:4.3.0.Final")
  testLibrary("org.springframework.data:spring-data-jpa:1.8.0.RELEASE")
  testLibrary("org.springframework:spring-test:3.0.0.RELEASE")

  testImplementation("org.hsqldb:hsqldb:2.0.0")

  // limit to spring 5; spring 6 is tested in its separate module
  latestDepTestLibrary("org.hibernate:hibernate-entitymanager:5.+") // see spring-data-3.0:testing module
  latestDepTestLibrary("org.springframework.data:spring-data-commons:2.+") // see spring-data-3.0:testing module
  latestDepTestLibrary("org.springframework.data:spring-data-jpa:2.+") // see spring-data-3.0:testing module
  latestDepTestLibrary("org.springframework:spring-test:5.+") // see spring-data-3.0:testing module
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
    dependsOn(testStableSemconv)
  }
}
