val stableVersion = "1.22.1"
val alphaVersion = "1.22.1-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
