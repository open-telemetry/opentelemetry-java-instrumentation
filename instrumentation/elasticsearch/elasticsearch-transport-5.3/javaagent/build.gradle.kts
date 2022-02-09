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
    skip("7.11.0")
    assertInverse.set(true)
  }
  pass {
    group.set("org.elasticsearch")
    module.set("elasticsearch")
    versions.set("[5.3.0,6.0.0)")
    // version 7.11.0 depends on org.elasticsearch:elasticsearch-plugin-classloader:7.11.0
    // which does not exist
    skip("7.11.0")
    assertInverse.set(true)
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

  implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:library"))

  testInstrumentation(project(":instrumentation:apache-httpasyncclient-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-data-1.8:javaagent"))

  testImplementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:testing"))
  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")

  // Unfortunately spring-data-elasticsearch requires 5.5.0
  testLibrary("org.elasticsearch.client:transport:5.5.0")
  testLibrary("org.elasticsearch.plugin:transport-netty3-client:5.3.0")

  testLibrary("org.springframework.data:spring-data-elasticsearch:3.0.0.RELEASE")

  latestDepTestLibrary("org.elasticsearch.plugin:transport-netty3-client:5.+") // see elasticsearch-transport-6.0 module
  latestDepTestLibrary("org.elasticsearch.client:transport:5.+") // see elasticsearch-transport-6.0 module
  latestDepTestLibrary("org.springframework.data:spring-data-elasticsearch:3.0.+")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.elasticsearch.experimental-span-attributes=true")
}
