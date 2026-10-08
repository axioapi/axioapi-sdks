# frozen_string_literal: true

module AxioAPI
  # Base class. Every API error carries the HTTP status, error code and request_id for support.
  class Error < StandardError
    attr_reader :status, :code, :request_id, :fields, :body

    def initialize(message, status: nil, code: nil, request_id: nil, fields: nil, body: nil)
      super(message)
      @status = status
      @code = code
      @request_id = request_id
      @fields = fields || {}
      @body = body
    end

    def to_s
      parts = [super]
      parts << "status=#{status}" if status
      parts << "code=#{code}" if code
      parts << "request_id=#{request_id}" if request_id
      parts.join(' | ')
    end
  end

  class AuthenticationError < Error; end        # 401
  class InsufficientCreditsError < Error; end   # 402
  class NotFoundError < Error; end              # 404
  class ValidationError < Error; end            # 422, see #fields
  class ConnectionError < Error; end            # no HTTP response (DNS, TLS, timeout)

  # 429. #retry_after is in seconds when the server sent it.
  class RateLimitError < Error
    attr_reader :retry_after

    def initialize(message, retry_after: nil, **kwargs)
      super(message, **kwargs)
      @retry_after = retry_after
    end
  end
end
