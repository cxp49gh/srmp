import unittest
import asyncio

from app.java_tools import JavaToolGateway


class JavaToolGatewayHeaderTest(unittest.TestCase):
    def test_headers_sanitize_non_ascii_trace_id_for_http_headers(self):
        gateway = JavaToolGateway()
        try:
            headers = gateway._headers(tenant_id="default", trace_id="codex-full-template-病害复核")
        finally:
            asyncio.run(gateway.close())

        self.assertEqual("default", headers["X-Tenant-Id"])
        self.assertEqual("codex-full-template-", headers["X-AI-Trace-Id"])
        headers["X-AI-Trace-Id"].encode("ascii")


if __name__ == "__main__":
    unittest.main()
