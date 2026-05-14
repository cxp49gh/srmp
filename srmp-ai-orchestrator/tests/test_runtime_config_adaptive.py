import unittest

from fastapi.testclient import TestClient

from app.main import app


class RuntimeConfigAdaptiveTest(unittest.TestCase):
    def test_runtime_config_exposes_adaptive_settings(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/runtime/config")

        self.assertEqual(200, response.status_code)
        safe_config = response.json()["safeConfig"]
        self.assertIn("adaptivePlanningEnabled", safe_config)
        self.assertIn("maxAdaptiveIterations", safe_config)
        self.assertIn("maxAdaptiveAddedTools", safe_config)
        self.assertIsInstance(safe_config["adaptivePlanningEnabled"], bool)
        self.assertGreaterEqual(safe_config["maxAdaptiveIterations"], 0)
        self.assertGreaterEqual(safe_config["maxAdaptiveAddedTools"], 0)


if __name__ == "__main__":
    unittest.main()
