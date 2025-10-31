plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.ning")
    module.set("async-http-client")
    versions.set("[1.8.0,1.9.0)")
    assertInverse.set(true)
    extraDependency("org.glassfish.grizzly:grizzly-http:2.3")
    extraDependency("commons-httpclient:commons-httpclient:3.1")
  }
}

dependencies {
  library("com.ning:async-http-client:1.8.3")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:netty:netty-3.8:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")

    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("async.https.skip", "true")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}

// async-http-client 1.8.x does not work with Netty versions newer than this due to referencing an
// internal file.
configurations.configureEach {
  if (!name.contains("muzzle")) {
    resolutionStrategy {
      eachDependency {
        // specifying a fixed version for all libraries with io.netty' group
        if (requested.group == "io.netty" && requested.name != "netty-bom") {
          useVersion("3.9.0.Final")
        }
      }
    }
  }
}
