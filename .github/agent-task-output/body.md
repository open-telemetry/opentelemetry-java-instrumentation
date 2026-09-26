Record Camel stable messaging fallback metrics when span creation is suppressed, including when the parent context carries the exporter-suppression marker.

Update coverage to verify that `messaging.process.duration` and `messaging.client.consumed.messages` are emitted in this case, while retaining checks that only missing metrics are recorded.
