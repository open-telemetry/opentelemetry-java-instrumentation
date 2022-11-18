val stableVersion = "1.20.1"
val alphaVersion = "1.20.1-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
