plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish.grizzly")
    module.set("grizzly-http")
    versions.set("[2.3,)")
    assertInverse.set(true)
    // 5.0.0 depends on org.glassfish.grizzly:grizzly-bom:5.0.0-SNAPSHOT which is not available
    skip("5.0.0")
  }
}

dependencies {
  // library("org.glassfish.grizzly:grizzly-http:2.3")
  compileOnly("org.glassfish.grizzly:grizzly-http:2.3")

  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  // testLibrary("org.glassfish.grizzly:grizzly-http-server:2.3")
  testImplementation("org.glassfish.grizzly:grizzly-http-server:2.3")

  // 5.0.0 depends on org.glassfish.grizzly:grizzly-bom:5.0.0-SNAPSHOT which is not available
  latestDepTestLibrary("org.glassfish.grizzly:grizzly-http:4.+") // documented limitation
  latestDepTestLibrary("org.glassfish.grizzly:grizzly-http-server:4.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}

// Requires old Guava. Can't use enforcedPlatform since predates BOM
configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.google.guava:guava:19.0")
