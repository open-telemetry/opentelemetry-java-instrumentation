The release workflow signs the Java agent JAR and SBOM bundle with keyless Sigstore Cosign, verifies both before publication, and publishes their Sigstore bundles. GitHub provenance attestations remain available.

Releases must now run from an immutable `vX.Y.Z` tag that matches the project version. The workflow limits OIDC signing and release publication permissions to a dedicated job.

`RELEASING.md` documents tag creation, command-line workflow dispatch, and artifact verification against the exact workflow and tag identity.
