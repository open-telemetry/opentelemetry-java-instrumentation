# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

"""Bounded OTLP trace receiver for the Quarkus overhead benchmark."""

import argparse
import collections
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from google.protobuf.message import DecodeError
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import (
    ExportTraceServiceRequest,
)


class Telemetry:
    def __init__(self):
        self.lock = threading.Lock()
        self.batches = 0
        self.errors = 0
        self.spans = collections.Counter()
        self.scopes = collections.Counter()
        self.preflight = []

    def record(self, request):
        with self.lock:
            self.batches += 1
            for resource in request.resource_spans:
                for scope in resource.scope_spans:
                    for span in scope.spans:
                        self.spans[str(span.kind)] += 1
                        self.scopes[scope.scope.name] += 1
                        if (
                            span.trace_id.hex().startswith("000000000000000000000000")
                            and len(self.preflight) < 200
                        ):
                            self.preflight.append(
                                {
                                    "trace_id": span.trace_id.hex(),
                                    "span_id": span.span_id.hex(),
                                    "parent_id": span.parent_span_id.hex(),
                                    "kind": span.kind,
                                    "name": span.name,
                                    "scope": scope.scope.name,
                                    "attributes": {
                                        attribute.key: attribute.value.string_value
                                        for attribute in span.attributes
                                    },
                                }
                            )

    def snapshot(self):
        with self.lock:
            return {
                "batches": self.batches,
                "errors": self.errors,
                "kinds": dict(self.spans),
                "scopes": dict(self.scopes),
                "preflight": list(self.preflight),
            }


def handler(telemetry):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_GET(self):
            if self.path != "/stats":
                self.send_error(404)
                return
            body = json.dumps(telemetry.snapshot()).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            length = self.headers.get("Content-Length", "")
            if (
                self.path != "/v1/traces"
                or not length.isdigit()
                or not 0 < int(length) <= 8 * 1024 * 1024
                or self.headers.get("Content-Encoding", "identity") != "identity"
                or self.headers.get("Content-Type") != "application/x-protobuf"
            ):
                with telemetry.lock:
                    telemetry.errors += 1
                self.send_error(400, "Expected an uncompressed OTLP protobuf request")
                return
            request = ExportTraceServiceRequest()
            try:
                request.ParseFromString(self.rfile.read(int(length)))
            except DecodeError:
                with telemetry.lock:
                    telemetry.errors += 1
                self.send_error(400, "Invalid OTLP protobuf")
                return
            telemetry.record(request)
            self.send_response(200)
            self.send_header("Content-Type", "application/x-protobuf")
            self.send_header("Content-Length", "0")
            self.end_headers()

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=14318)
    args = parser.parse_args()
    with ThreadingHTTPServer(("127.0.0.1", args.port), handler(Telemetry())) as server:
        print(f"OTLP receiver listening on {server.server_address}", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
