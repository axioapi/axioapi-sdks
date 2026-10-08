import os
import unittest
import urllib.request

from axioapi import (APIConnectionError, AuthenticationError, AxioAPI, AxioAPIError, InsufficientCreditsError,
                     NotFoundError, RateLimitError, ValidationError)

URL = os.environ.get("AXIOAPI_MOCK_URL", "http://127.0.0.1:8765")


def client(**kwargs):
    kwargs.setdefault("sleep", lambda s: None)
    return AxioAPI("test_key", base_url=URL, **kwargs)


class ClientTest(unittest.TestCase):
    def setUp(self):
        urllib.request.urlopen(URL + "/__reset").read()

    def test_requires_key(self):
        os.environ.pop("AXIOAPI_KEY", None)
        with self.assertRaises(ValueError):
            AxioAPI(base_url=URL)

    def test_namespace_post_sends_json_body_and_unwraps_data(self):
        data = client().seo.keyword_metrics(keywords=["api gateway", "proxy scraper"], country="us")
        self.assertEqual(data["method"], "POST")
        self.assertEqual(data["path"], "/api/v1/seo/keywords/metrics")
        self.assertEqual(data["body"], {"keywords": ["api gateway", "proxy scraper"], "country": "us"})
        self.assertEqual(data["content_type"], "application/json")
        self.assertTrue(data["user_agent"].startswith("axioapi-python/"))

    def test_path_params_are_encoded_and_query_is_separated(self):
        data = client().call("seo.backlinks-summary", domain="exa mple.com")
        self.assertEqual(data["path"], "/api/v1/seo/domains/exa%20mple.com/backlinks")
        data = client().seo.keyword_suggestions(q="laravel api", country="us", lang="en")
        self.assertEqual(data["query"], {"q": ["laravel api"], "country": ["us"], "lang": ["en"]})

    def test_missing_path_param_and_unknown_operation(self):
        with self.assertRaises(ValueError):
            client().call("seo.backlinks-summary")
        with self.assertRaises(ValueError):
            client().call("nope.nothing")
        with self.assertRaises(AttributeError):
            client().seo.does_not_exist
        with self.assertRaises(AttributeError):
            client().nothing

    def test_every_registry_operation_is_reachable_by_attribute(self):
        c = client()
        for key in c.operations():
            group, _, rest = key.partition(".")
            self.assertIsNotNone(c.registry.resolve(group, rest), key)

    def test_none_params_are_dropped(self):
        data = client().seo.keyword_suggestions(q="x", country=None)
        self.assertEqual(data["query"], {"q": ["x"]})

    def test_raw_returns_envelope(self):
        env = client().request("GET", "/api/v1/account/limits", raw=True)
        self.assertEqual(env["status"], "success")
        self.assertEqual(env["request"]["id"], "req_test_1")

    def test_binary_download_returns_bytes(self):
        body = client().call("ai-image.artifact", job="abc")
        self.assertIsInstance(body, bytes)
        self.assertTrue(body.startswith(b"\x89PNG"))

    def test_errors_map_to_classes(self):
        with self.assertRaises(AuthenticationError) as ctx:
            AxioAPI("wrong", base_url=URL).account.limits()
        self.assertEqual(ctx.exception.status, 401)
        self.assertEqual(ctx.exception.request_id, "req_test_1")
        with self.assertRaises(InsufficientCreditsError):
            AxioAPI("nocredit", base_url=URL).account.limits()
        with self.assertRaises(NotFoundError):
            client().request("GET", "/api/v1/temp-mail/inboxes/missing")
        with self.assertRaises(ValidationError) as ctx:
            client().seo.on_page_audit()
        self.assertEqual(ctx.exception.fields, {"url": ["The url field is required."]})
        with self.assertRaises(AxioAPIError) as ctx:
            client().request("GET", "/api/v1/boom")
        self.assertEqual(ctx.exception.status, 500)

    def test_retries_idempotent_503_then_succeeds(self):
        self.assertEqual(client().request("GET", "/api/v1/flaky")["attempts"], 3)

    def test_retries_429_for_get_and_gives_up_with_retry_after(self):
        self.assertEqual(client().request("GET", "/api/v1/ratelimited")["attempts"], 2)
        with self.assertRaises(RateLimitError) as ctx:
            client(max_retries=1).request("POST", "/api/v1/always429", json_body={})
        self.assertEqual(ctx.exception.retry_after, 7.0)
        hits = urllib.request.urlopen(URL + "/__hits").read().decode()
        self.assertIn('"always429": 2', hits)

    def test_post_503_is_not_retried(self):
        with self.assertRaises(AxioAPIError):
            client().request("POST", "/api/v1/post503", json_body={})
        self.assertIn('"post503": 1', urllib.request.urlopen(URL + "/__hits").read().decode())

    def test_connection_error(self):
        with self.assertRaises(APIConnectionError):
            AxioAPI("k", base_url="http://127.0.0.1:1", max_retries=0, timeout=2).account.limits()


if __name__ == "__main__":
    unittest.main()
