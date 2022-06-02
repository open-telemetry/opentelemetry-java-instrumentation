plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-http_2.11")
    versions.set("[10.0.0,10.1.0)")
    // later versions of akka-http expect streams to be provided
    extraDependency("com.typesafe.akka:akka-stream_2.11:2.4.14")
  }
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-http_2.12")
    versions.set("[10.0.0,10.1.0)")
    // later versions of akka-http expect streams to be provided
    extraDependency("com.typesafe.akka:akka-stream_2.12:2.4.14")
  }
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-http_2.11")
    versions.set("[10.1.0,)")
    // later versions of akka-http expect streams to be provided
    extraDependency("com.typesafe.akka:akka-stream_2.11:2.5.11")
  }
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-http_2.12")
    versions.set("[10.1.0,)")
    // later versions of akka-http expect streams to be provided
    extraDependency("com.typesafe.akka:akka-stream_2.12:2.5.11")
  }
  // There is no akka-http 10.0.x series for scala 2.13
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-http_2.13")
    versions.set("[10.1.8,)")
    // later versions of akka-http expect streams to be provided
    extraDependency("com.typesafe.akka:akka-stream_2.13:2.5.23")
  }
}

dependencies {
  library("com.typesafe.akka:akka-http_2.11:10.0.0")
  library("com.typesafe.akka:akka-stream_2.11:2.4.14")

  // these instrumentations are not needed for the tests to pass
  // they are here to test for context leaks
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.5:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-fork-join-2.5:javaagent"))

  latestDepTestLibrary("com.typesafe.akka:akka-http_2.13:+")
  latestDepTestLibrary("com.typesafe.akka:akka-stream_2.13:+")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  //The thread propagation debug entry on the Context causes thread leakage with the way our Akka HTTP is instrumented
  //This is because currentContext != propagatedContext when the propagation is on the same thread as the original
  //Ideally, a Context equality check should disregard the thread-propagation-locations key
  jvmArgs("-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=false")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // akka artifact name is different for regular and latest tests
    testImplementation {
      exclude("com.typesafe.akka", "akka-http_2.11")
      exclude("com.typesafe.akka", "akka-stream_2.11")
    }
  }
}
