#!/usr/bin/env python3
"""OpenAI-compatible test double for the executable R5 browser proof.

It proves request shape, structured-result handling, provenance display, and browser integration;
it deliberately does not prove Ministral quality, LM Studio compatibility, or hardware reachability.
"""

from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--request-log", type=Path, required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.request_log.parent.mkdir(parents=True, exist_ok=True)

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
            if self.path != "/v1/models":
                self.send_error(404)
                return
            self._json(200, {"data": [{"id": args.model}]})

        def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
            if self.path != "/v1/chat/completions":
                self.send_error(404)
                return
            length = int(self.headers.get("Content-Length", "0"))
            request_body = self.rfile.read(length).decode("utf-8")
            with args.request_log.open("a", encoding="utf-8") as log:
                log.write(request_body)
                log.write("\n")
            analysis = {
                "riskLevel": "HIGH",
                "findingsSummary": (
                    "The synthetic scenario combines repeated declines, high-value cross-border "
                    "payments, crypto activity, retained source-risk evidence and applicable policy."
                ),
                "recommendations": [
                    "Review the retained source transactions and policy evidence.",
                    "Escalate the synthetic case for documented operator review.",
                ],
            }
            self._json(
                200,
                {
                    "id": "chatcmpl-r5-contract-proof",
                    "object": "chat.completion",
                    "created": 1,
                    "model": args.model,
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": json.dumps(analysis, separators=(",", ":")),
                            },
                            "finish_reason": "stop",
                        }
                    ],
                    "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
                },
            )

        def log_message(self, _format: str, *_args: object) -> None:
            return

        def _json(self, status: int, body: object) -> None:
            encoded = json.dumps(body, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

    ThreadingHTTPServer((args.bind, args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
