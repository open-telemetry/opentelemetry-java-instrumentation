# Candidate critique

Rejected candidate-001. Its released-version premise is contradicted by upstream source:

- [ParsingErrorHandler in v2.0.0-M1](https://github.com/apache/pekko-http/blob/v2.0.0-M1/http-core/src/main/scala/org/apache/pekko/http/ParsingErrorHandler.scala) declares only the four-argument `handle`.
- [HttpServerBluePrint in v2.0.0-M1](https://github.com/apache/pekko-http/blob/v2.0.0-M1/http-core/src/main/scala/org/apache/pekko/http/impl/engine/server/HttpServerBluePrint.scala) invokes `handle(status, info, log, settings)`.
- The five-argument overload exists on upstream main, but not in that latest listed milestone tag. Its default implementation delegates to the four-argument overload, and its documentation explicitly preserves the default handler's four-argument implementation for instrumentation compatibility.

The frozen diff matches the released four-argument signature and explicitly leaves richer Pekko HTTP 2.0 instrumentation for version-specific work. A future custom override could bypass this advice, but the candidate does not establish that failure in a released version. The open-ended Muzzle range alone does not substantiate the claimed regression. No review comment retained.

Validation was source-based; no repository code was modified or tests run.
