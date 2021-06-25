plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.google.http-client")
    module.set("google-http-client")

    // 1.19.0 is the first release.  The versions before are betas and RCs
    versions.set("[1.19.0,)")
  }
}

dependencies {
  library("com.google.http-client:google-http-client:1.19.0")
}
