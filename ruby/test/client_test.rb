# frozen_string_literal: true

require 'minitest/autorun'
require 'net/http'
require 'json'
require_relative '../lib/axioapi'

class ClientTest < Minitest::Test
  URL = ENV['AXIOAPI_MOCK_URL'] || 'http://127.0.0.1:8765'

  def setup
    Net::HTTP.get(URI("#{URL}/__reset"))
  end

  def client(key = 'test_key', **opts)
    AxioAPI::Client.new(key, base_url: URL, sleeper: ->(_s) {}, **opts)
  end

  def hits
    JSON.parse(Net::HTTP.get(URI("#{URL}/__hits")))
  end

  def test_requires_key
    ENV.delete('AXIOAPI_KEY')
    assert_raises(ArgumentError) { AxioAPI::Client.new(nil, base_url: URL) }
  end

  def test_group_call_sends_json_body_and_unwraps_data
    data = client.seo.keyword_metrics(keywords: ['api gateway', 'proxy scraper'], country: 'us')
    assert_equal 'POST', data['method']
    assert_equal '/api/v1/seo/keywords/metrics', data['path']
    assert_equal({ 'keywords' => ['api gateway', 'proxy scraper'], 'country' => 'us' }, data['body'])
    assert_equal 'application/json', data['content_type']
    assert_match(%r{\Aaxioapi-ruby/}, data['user_agent'])
  end

  def test_camel_and_kebab_names_resolve
    c = client
    assert_equal '/api/v1/seo/keywords/metrics', c.seo.keywordMetrics(keywords: ['a'])['path']
    assert_equal '/api/v1/seo/keywords/metrics', c.call('seo.keyword-metrics', keywords: ['a'])['path']
  end

  def test_path_params_encoded_and_query_separated
    c = client
    assert_equal '/api/v1/seo/domains/exa%20mple.com/backlinks', c.call('seo.backlinks-summary', domain: 'exa mple.com')['path']
    data = c.seo.keyword_suggestions(q: 'laravel api', country: 'us', lang: 'en', skipped: nil)
    assert_equal({ 'q' => ['laravel api'], 'country' => ['us'], 'lang' => ['en'] }, data['query'])
  end

  def test_missing_path_param_unknown_operation_and_group
    c = client
    assert_raises(ArgumentError) { c.call('seo.backlinks-summary') }
    assert_raises(ArgumentError) { c.call('nope.nothing') }
    assert_raises(NoMethodError) { c.seo.does_not_exist }
    assert_raises(NoMethodError) { c.nothing }
  end

  def test_every_registry_operation_is_reachable
    c = client
    c.operations.each_key do |key|
      group, rest = key.split('.', 2)
      assert_equal key, c.registry.resolve(group, rest)
    end
    assert_operator c.operations.size, :>, 100
  end

  def test_raw_envelope
    env = client.request('GET', '/api/v1/account/limits', raw: true)
    assert_equal 'success', env['status']
    assert_equal 'req_test_1', env['request']['id']
  end

  def test_binary_download_returns_bytes
    body = client.call('ai-image.artifact', job: 'abc')
    assert_equal Encoding::ASCII_8BIT, body.encoding
    assert body.start_with?("\x89PNG".b)
  end

  def test_errors_map_to_classes
    e = assert_raises(AxioAPI::AuthenticationError) { client('wrong').account.limits }
    assert_equal 401, e.status
    assert_equal 'req_test_1', e.request_id
    assert_raises(AxioAPI::InsufficientCreditsError) { client('nocredit').account.limits }
    assert_raises(AxioAPI::NotFoundError) { client.request('GET', '/api/v1/temp-mail/inboxes/missing') }
    v = assert_raises(AxioAPI::ValidationError) { client.seo.on_page_audit }
    assert_equal({ 'url' => ['The url field is required.'] }, v.fields)
    other = assert_raises(AxioAPI::Error) { client.request('GET', '/api/v1/boom') }
    assert_equal 500, other.status
  end

  def test_retries_idempotent_503_then_succeeds
    assert_equal 3, client.request('GET', '/api/v1/flaky')['attempts']
  end

  def test_retries_429_gives_up_with_retry_after_and_post_503_not_retried
    assert_equal 2, client.request('GET', '/api/v1/ratelimited')['attempts']
    e = assert_raises(AxioAPI::RateLimitError) { client(max_retries: 1).request('POST', '/api/v1/always429', body: {}) }
    assert_equal 7.0, e.retry_after
    assert_equal 2, hits['always429']
    assert_raises(AxioAPI::Error) { client.request('POST', '/api/v1/post503', body: {}) }
    assert_equal 1, hits['post503']
  end

  def test_connection_error
    c = AxioAPI::Client.new('k', base_url: 'http://127.0.0.1:1', max_retries: 0, timeout: 2)
    assert_raises(AxioAPI::ConnectionError) { c.account.limits }
  end
end
