plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-context")
    versions.set("[3.1.0.RELEASE,]")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:spring:spring-scheduling-3.1:bootstrap"))

  library("org.springframework:spring-context:3.1.0.RELEASE")
}

testing {
  suites {
    // Spring 3.1 needs external CGLIB for @Configuration enhancement.
    register<JvmTestSuite>("testEnhanced") {
      sources.java.srcDir("src/test/java")
      dependencies {
        implementation("org.springframework:spring-context:${baseVersion("3.2.3.RELEASE").orLatest()}")
      }
      targets.configureEach {
        testTask.configure {
          useJUnitPlatform {
            includeTags("enhanced")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  test {
    useJUnitPlatform {
      excludeTags("enhanced")
    }
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
      excludeTags("enhanced")
    }
    jvmArgs("-Dotel.instrumentation.spring-scheduling.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.spring-scheduling.experimental-span-attributes=true")
  }

  val testEnhancedExperimental = register<Test>("testEnhancedExperimental") {
    testClassesDirs = sourceSets["testEnhanced"].output.classesDirs
    classpath = sourceSets["testEnhanced"].runtimeClasspath
    useJUnitPlatform {
      includeTags("enhanced")
    }
    jvmArgs("-Dotel.instrumentation.spring-scheduling.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.spring-scheduling.experimental-span-attributes=true")
  }

  check {
    dependsOn(testExperimental, testing.suites.named("testEnhanced"), testEnhancedExperimental)
  }
}

// spring 6 requires java 17
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
