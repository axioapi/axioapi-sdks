# frozen_string_literal: true

require 'json'
require_relative 'errors'

module AxioAPI
  module ResponseParser
    ERROR_BY_STATUS = {
      401 => AuthenticationError,
      402 => InsufficientCreditsError,
      404 => NotFoundError,
      422 => ValidationError
    }.freeze

    module_function

    # Returns `data`, the whole envelope when raw, or a binary String for non-JSON bodies (files).
    def parse(response, raw:)
      return response.body.b unless response.json?
      return nil if response.body.empty?

      envelope = JSON.parse(response.body)
      raw || !envelope.is_a?(Hash) ? envelope : envelope['data']
    end

    def error(response)
      body = json_hash(response.body)
      detail = body['error'].is_a?(Hash) ? body['error'] : {}
      message = detail['message'] || body['message'] || "HTTP #{response.status}"
      info = {
        status: response.status, code: detail['code'], request_id: detail['request_id'] || response.header('x-request-id'),
        fields: detail['fields'], body: body.empty? ? nil : body
      }
      return RateLimitError.new(message, retry_after: retry_after(response), **info) if response.status == 429

      ERROR_BY_STATUS.fetch(response.status, Error).new(message, **info)
    end

    def json_hash(text)
      parsed = JSON.parse(text)
      parsed.is_a?(Hash) ? parsed : {}
    rescue JSON::ParserError
      {}
    end

    def retry_after(response)
      value = response.header('retry-after')
      value&.match?(/\A\d+\z/) ? value.to_f : nil
    end
  end
end
