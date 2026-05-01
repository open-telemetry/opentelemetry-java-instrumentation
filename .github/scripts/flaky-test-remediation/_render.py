"""Shared formatting helpers for prompt and PR-body rendering."""

import datetime as dt


def utc_day(start_ms):
    return dt.datetime.fromtimestamp(
        start_ms / 1000, tz=dt.timezone.utc).strftime("%Y-%m-%d")
