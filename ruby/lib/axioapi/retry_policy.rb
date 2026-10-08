# frozen_string_literal: true

module AxioAPI
  # 429 is retried for every method; 5xx and network errors only for idempotent ones.
  class RetryPolicy
    RETRYABLE_STATUS = [502, 503, 504].freeze
    IDEMPOTENT_METHODS = %w[GET HEAD DELETE].freeze
    MAX_DELAY_SECONDS = 8.0
    MAX_RETRY_AFTER_SECONDS = 30.0

    attr_reader :max_retries

    def initialize(max_retries:, sleeper:)
      @max_retries = [max_retries, 0].max
      @sleeper = sleeper
    end

    def retry_status?(status, method, attempt)
      return false if attempt >= max_retries

      status == 429 || (IDEMPOTENT_METHODS.include?(method) && RETRYABLE_STATUS.include?(status))
    end

    def retry_connection?(method, attempt)
      attempt < max_retries && IDEMPOTENT_METHODS.include?(method)
    end

    def wait(attempt, retry_after = nil)
      @sleeper.call(delay(attempt, retry_after))
    end

    private

    def delay(attempt, retry_after)
      return [retry_after.to_f, MAX_RETRY_AFTER_SECONDS].min if retry_after&.match?(/\A\d+(\.\d+)?\z/)

      [0.5 * (2**attempt), MAX_DELAY_SECONDS].min
    end
  end
end
