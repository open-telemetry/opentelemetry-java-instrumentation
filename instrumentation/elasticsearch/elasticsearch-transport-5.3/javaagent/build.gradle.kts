plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.elasticsearch.client")
    module.set("transport")
    versions.set("[5.3.0,6.0.0)")
    // version 7.11.0 depends on org.elasticsearch:elasticsearch:7.11.0 which depends on
    // org.elasticsearch:elasticsearch-plugin-classloader:7.11.0 which does not exist
    // version 7.17.8 has broken module metadata
    skip("7.11.0", "7.17.8")
    // version 8.8.0 depends on elasticsearch:elasticsearch-preallocate which doesn't exist
    excludeDependency("org.elasticsearch:elasticsearch-preallocate")
    assertInverse.set(true)
  }
  pass {
    group.set("org.elasticsearch")
    module.set("elasticsearch")
    versions.set("[5.3.0,6.0.0)")
    // version 7.11.0 depends on org.elasticsearch:elasticsearch-plugin-classloader:7.11.0
    // which does not exist
    skip("7.11.0")
    // version 8.8.0 depends on elasticsearch:elasticsearch-preallocate which doesn't exist
    excludeDependency("org.elasticsearch:elasticsearch-preallocate")
    assertInverse.set(true)
  }
}

if (findProperty("testLatestDeps") as Boolean) {
  // when running on jdk 21 Elasticsearch53SpringRepositoryTest occasionally fails with timeout
  otelJava {
    maxJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

dependencies {
  compileOnly("org.elasticsearch.client:transport:5.3.0") {
    isTransitive = false
  }
  compileOnly("org.elasticsearch:elasticsearch:5.3.0") {
    // We don't need all its transitive dependencies when compiling and run tests against 5.5.0
    isTransitive = false
  }

  implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:javaagent"))

  testInstrumentation(project(":instrumentation:apache-httpasyncclient-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-data:spring-data-1.8:javaagent"))

  testImplementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:testing"))
  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")

  // Unfortunately spring-data-elasticsearch requires 5.5.0
  testLibrary("org.elasticsearch.client:transport:5.5.0")
  testLibrary("org.elasticsearch.plugin:transport-netty3-client:5.3.0")

  testLibrary("org.springframework.data:spring-data-elasticsearch:3.0.0.RELEASE")

  latestDepTestLibrary("org.elasticsearch.plugin:transport-netty3-client:5.+") // see elasticsearch-transport-6.0 module
  latestDepTestLibrary("org.elasticsearch.client:transport:5.+") // see elasticsearch-transport-6.0 module
  latestDepTestLibrary("org.springframework.data:spring-data-elasticsearch:3.0.+") // see elasticsearch-transport-6.0 module
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("collectSpans", true)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database,code")
    systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testExperimental by registering(Test::class) {
    jvmArgs("-Dotel.instrumentation.elasticsearch.experimental-span-attributes=true")
    systemProperty("metaDataConfig", "otel.instrumentation.elasticsearch.experimental-span-attributes=true")
  }

  check {
    dependsOn(testStableSemconv, testExperimental)
  }
}
