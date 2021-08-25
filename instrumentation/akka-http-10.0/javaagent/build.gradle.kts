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
  testInstrumentation(project(":instrumentation:akka-actor-2.5:javaagent"))
  testInstrumentation(project(":instrumentation:akka-actor-fork-join-2.5:javaagent"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
}
