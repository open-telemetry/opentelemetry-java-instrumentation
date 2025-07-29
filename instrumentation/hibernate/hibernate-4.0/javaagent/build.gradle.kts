plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.hibernate")
    module.set("hibernate-core")
    versions.set("[4.0.0.Final,6)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.hibernate:hibernate-core:4.0.0.Final")

  implementation(project(":instrumentation:hibernate:hibernate-common:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  // Added to ensure cross compatibility:
  testInstrumentation(project(":instrumentation:hibernate:hibernate-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-procedure-call-4.3:javaagent"))

  testImplementation("com.h2database:h2:1.4.197")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")
  testImplementation("javax.activation:activation:1.1.1")
  testImplementation("org.hsqldb:hsqldb:2.0.0")
  // First version to work with Java 14
  testImplementation("org.springframework.data:spring-data-jpa:1.8.0.RELEASE")

  testImplementation("org.hibernate:hibernate-core:4.0.0.Final")
  testImplementation("org.hibernate:hibernate-entitymanager:4.0.0.Final")

  testImplementation("org.javassist:javassist:3.28.0-GA")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
testing {
  suites {
    val version5Test by registering(JvmTestSuite::class) {
      dependencies {
        sources {
          java {
            setSrcDirs(listOf("src/test/java"))
          }
          resources {
            setSrcDirs(listOf("src/test/resources"))
          }
        }

        implementation("com.h2database:h2:1.4.197")
        implementation("javax.xml.bind:jaxb-api:2.2.11")
        implementation("com.sun.xml.bind:jaxb-core:2.2.11")
        implementation("com.sun.xml.bind:jaxb-impl:2.2.11")
        implementation("javax.activation:activation:1.1.1")
        implementation("org.hsqldb:hsqldb:2.0.0")

        if (latestDepTest) {
          implementation("org.hibernate:hibernate-core:5.0.0.Final")
          implementation("org.hibernate:hibernate-entitymanager:5.0.0.Final")
          implementation("org.springframework.data:spring-data-jpa:2.3.0.RELEASE")
        } else {
          implementation("org.hibernate:hibernate-core:5.+")
          implementation("org.hibernate:hibernate-entitymanager:5.+")
          implementation("org.springframework.data:spring-data-jpa:(2.4.0,3)")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")

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
