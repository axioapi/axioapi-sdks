# frozen_string_literal: true

require 'json'
require 'uri'
require_relative 'errors'
require_relative 'naming'
require_relative 'operation_group'
require_relative 'registry'
require_relative 'request_builder'
require_relative 'response_parser'
require_relative 'retry_policy'
require_relative 'transport'

module AxioAPI
  DEFAULT_BASE_URL = 'https://axioapi.com'

  class Client
    attr_reader :api_key, :base_url, :registry

    # Options: base_url:, timeout: (seconds), max_retries:, user_agent:, sleeper: (callable, for tests).
    def initialize(api_key = nil, base_url: nil, timeout: 30, max_retries: 2, user_agent: nil, sleeper: ->(seconds) { sleep(seconds) })
      @api_key = api_key || ENV['AXIOAPI_KEY']
      raise ArgumentError, 'Pass an API key or set the AXIOAPI_KEY environment variable.' if @api_key.nil? || @api_key.empty?

      @base_url = (base_url || ENV['AXIOAPI_BASE_URL'] || DEFAULT_BASE_URL).sub(%r{/+\z}, '')
      @user_agent = user_agent || "axioapi-ruby/#{AxioAPI::VERSION}"
      @registry = Registry.load
      @retry = RetryPolicy.new(max_retries: max_retries, sleeper: sleeper)
      @transport = Transport.new(timeout: timeout)
    end

    def operations
      registry.all
    end

    def method_missing(name, *args)
      return super unless args.empty? && registry.group?(name)

      OperationGroup.new(self, name)
    end

    def respond_to_missing?(name, include_private = false)
      registry.group?(name) || super
    end

    # Calls an operation by capability key (for example 'seo.keyword-metrics') and returns `data`.
    def call(operation, **params)
      found = registry.find(operation)
      raise ArgumentError, "Unknown operation '#{operation}'. See #operations." unless found

      prepared = RequestBuilder.build(found, params)
      request(prepared.http_method, prepared.path, query: prepared.query, body: prepared.body)
    end

    # Sends a request with retries; returns `data`, the whole envelope with raw: true, or a binary String for files.
    def request(method, path, query: {}, body: nil, raw: false)
      method = method.to_s.upcase
      uri = build_uri(path, query)
      payload = body.nil? ? nil : JSON.generate(body)
      headers = build_headers(has_body: !payload.nil?)
      attempt = 0

      loop do
        response = send_once(method, uri, headers, payload, attempt)
        if response.nil?
          attempt += 1
          next
        end
        return ResponseParser.parse(response, raw: raw) if response.success?

        raise ResponseParser.error(response) unless @retry.retry_status?(response.status, method, attempt)

        @retry.wait(attempt, response.header('retry-after'))
        attempt += 1
      end
    end

    private

    # Returns the response, or nil after waiting when a connection failure should be retried.
    def send_once(method, uri, headers, payload, attempt)
      @transport.send_request(method, uri, headers, payload)
    rescue Transport::ConnectionFailure => e
      raise ConnectionError, "Could not reach #{base_url}: #{e.message}" unless @retry.retry_connection?(method, attempt)

      @retry.wait(attempt)
      nil
    end

    def build_uri(path, query)
      uri = URI.parse(base_url + (path.start_with?('/') ? path : "/#{path}"))
      uri.query = RequestBuilder.encode_query(query) unless query.nil? || query.empty?
      uri
    end

    def build_headers(has_body:)
      headers = { 'Authorization' => "Bearer #{api_key}", 'Accept' => 'application/json', 'User-Agent' => @user_agent }
      headers['Content-Type'] = 'application/json' if has_body
      headers
    end
  end
end
