Uses configured Redis URI targets for Lettuce 5.0 spans when stable database semantic conventions are enabled. Under v3-preview, the advice-based module handles Lettuce 5.0 and later and applies the same target capture across supported versions.

This covers standalone, batch, reactive, Pub/Sub, master-replica, Sentinel, and cluster telemetry. Ordinary mode keeps the existing Lettuce 5.0/5.1 module split.
