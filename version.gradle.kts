val stableVersion = "1.25.1"
val alphaVersion = "1.25.1-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
