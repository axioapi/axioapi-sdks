# frozen_string_literal: true

require 'net/http'
require 'openssl'
require 'uri'

module AxioAPI
  HttpResponse = Struct.new(:status, :headers, :body, keyword_init: true) do
    def header(name)
      headers[name.downcase]
    end

    def success?
      status.between?(200, 299)
    end

    def json?
      header('content-type').to_s.include?('json')
    end
  end

  # One HTTP round trip with Net::HTTP; HTTP error statuses are returned, not raised.
  class Transport
    # Raised when the request produced no HTTP response.
    class ConnectionFailure < StandardError; end

    NETWORK_ERRORS = [SocketError, SystemCallError, Net::OpenTimeout, Net::ReadTimeout, OpenSSL::SSL::SSLError, EOFError].freeze

    def initialize(timeout:)
      @timeout = timeout
    end

    def send_request(method, uri, headers, payload)
      response = build_http(uri).request(build_request(method, uri, headers, payload))
      HttpResponse.new(status: response.code.to_i, headers: response.to_hash.transform_values(&:first), body: response.body.to_s)
    rescue *NETWORK_ERRORS => e
      raise ConnectionFailure, e.message
    end

    private

    def build_http(uri)
      Net::HTTP.new(uri.host, uri.port).tap do |http|
        http.use_ssl = uri.scheme == 'https'
        http.open_timeout = @timeout
        http.read_timeout = @timeout
      end
    end

    def build_request(method, uri, headers, payload)
      Net::HTTPGenericRequest.new(method, !payload.nil?, method != 'HEAD', uri.request_uri).tap do |request|
        headers.each { |name, value| request[name] = value }
        request.body = payload if payload
      end
    end
  end
end
