# frozen_string_literal: true

require 'json'
require 'net/http'
require 'uri'

module AxioAPI
  DEFAULT_BASE_URL = 'https://axioapi.com'
  RETRY_STATUS = [502, 503, 504].freeze
  OPERATIONS_PATH = File.join(__dir__, 'operations.json')

  # `client.seo`: method-call access to every operation of one API group.
  class OperationGroup
    def initialize(client, group)
      @client = client
      @group = group
    end

    def method_missing(name, **params)
      key = @client.resolve(@group, name)
      return super unless key

      @client.call(key, **params)
    end

    def respond_to_missing?(name, include_private = false)
      !@client.resolve(@group, name).nil? || super
    end
  end

  class Client
    attr_reader :api_key, :base_url

    # Options: base_url:, timeout: (seconds), max_retries:, user_agent:, sleeper: (callable, for tests).
    def initialize(api_key = nil, base_url: nil, timeout: 30, max_retries: 2, user_agent: nil, sleeper: ->(s) { sleep(s) })
      @api_key = api_key || ENV['AXIOAPI_KEY']
      raise ArgumentError, 'Pass an API key or set the AXIOAPI_KEY environment variable.' if @api_key.nil? || @api_key.empty?

      @base_url = (base_url || ENV['AXIOAPI_BASE_URL'] || DEFAULT_BASE_URL).sub(%r{/+\z}, '')
      @timeout = timeout
      @max_retries = [max_retries, 0].max
      @user_agent = user_agent || "axioapi-ruby/#{AxioAPI::VERSION}"
      @sleeper = sleeper
      @operations = JSON.parse(File.read(OPERATIONS_PATH, encoding: 'UTF-8'))['operations']
      @index = {}
      @groups = {}
      @operations.each_key do |key|
        group, rest = key.split('.', 2)
        @groups[normalize(group)] = true
        @index["#{normalize(group)}.#{normalize(rest)}"] = key
      end
    end

    # Registry of every operation: method, path, parameters and credit cost.
    def operations
      @operations.dup
    end

    # Capability key for a group and operation name (snake_case, camelCase or kebab-case), or nil.
    def resolve(group, name)
      @index["#{normalize(group)}.#{normalize(name)}"]
    end

    def method_missing(name, *args)
      return super unless args.empty? && @groups.key?(normalize(name))

      OperationGroup.new(self, name)
    end

    def respond_to_missing?(name, include_private = false)
      @groups.key?(normalize(name)) || super
    end

    # Call an operation by capability key, e.g. call('seo.keyword-metrics', keywords: [...]). Returns `data`.
    def call(operation, **params)
      key = @operations.key?(operation.to_s) ? operation.to_s : nil
      if key.nil? && operation.to_s.include?('.')
        group, rest = operation.to_s.split('.', 2)
        key = resolve(group, rest)
      end
      raise ArgumentError, "Unknown operation '#{operation}'. See #operations." unless key

      op = @operations[key]
      path = op['path'].dup
      params = params.transform_keys(&:to_s)
      op['path_params'].each do |name|
        raise ArgumentError, "Missing path parameter '#{name}' for #{key}" unless params.key?(name)

        path.sub!("{#{name}}", URI.encode_www_form_component(params.delete(name).to_s).gsub('+', '%20'))
      end
      has_body = !%w[GET DELETE HEAD].include?(op['method'])
      query = {}
      body = {}
      params.each do |name, value|
        next if value.nil?

        if has_body && (op['body'].include?(name) || !op['query'].include?(name))
          body[name] = value
        else
          query[name] = value
        end
      end
      request(op['method'], path, query: query, body: has_body && !body.empty? ? body : nil)
    end

    # Send a request. Returns `data`, the whole envelope with raw: true, or a binary String for file downloads.
    def request(method, path, query: {}, body: nil, raw: false)
      method = method.to_s.upcase
      uri = URI.parse(@base_url + (path.start_with?('/') ? path : "/#{path}"))
      uri.query = build_query(query) unless query.nil? || query.empty?
      payload = body.nil? ? nil : JSON.generate(body)
      idempotent = %w[GET HEAD DELETE].include?(method)
      attempt = 0

      loop do
        begin
          response = perform(method, uri, payload)
        rescue SocketError, SystemCallError, Net::OpenTimeout, Net::ReadTimeout, OpenSSL::SSL::SSLError, EOFError => e
          if idempotent && attempt < @max_retries
            @sleeper.call(delay(nil, attempt))
            attempt += 1
            next
          end
          raise ConnectionError, "Could not reach #{@base_url}: #{e.message}"
        end

        status = response.code.to_i
        return parse(response, raw) if status.between?(200, 299)

        if (status == 429 || (idempotent && RETRY_STATUS.include?(status))) && attempt < @max_retries
          @sleeper.call(delay(response['Retry-After'], attempt))
          attempt += 1
          next
        end
        raise build_error(status, response)
      end
    end

    private

    def perform(method, uri, payload)
      http = Net::HTTP.new(uri.host, uri.port)
      http.use_ssl = uri.scheme == 'https'
      http.open_timeout = @timeout
      http.read_timeout = @timeout
      req = Net::HTTPGenericRequest.new(method, !payload.nil?, method != 'HEAD', uri.request_uri)
      req['Authorization'] = "Bearer #{@api_key}"
      req['Accept'] = 'application/json'
      req['User-Agent'] = @user_agent
      if payload
        req['Content-Type'] = 'application/json'
        req.body = payload
      end
      http.request(req)
    end

    def parse(response, raw)
      return response.body.to_s.b unless response['Content-Type'].to_s.include?('json')
      return nil if response.body.nil? || response.body.empty?

      envelope = JSON.parse(response.body)
      raw || !envelope.is_a?(Hash) ? envelope : envelope['data']
    end

    def build_error(status, response)
      body = begin
        JSON.parse(response.body.to_s)
      rescue JSON::ParserError
        nil
      end
      body = {} unless body.is_a?(Hash)
      err = body['error'].is_a?(Hash) ? body['error'] : {}
      info = {
        status: status, code: err['code'], request_id: err['request_id'] || response['X-Request-Id'],
        fields: err['fields'], body: body.empty? ? nil : body
      }
      message = err['message'] || body['message'] || "HTTP #{status}"
      case status
      when 401 then AuthenticationError.new(message, **info)
      when 402 then InsufficientCreditsError.new(message, **info)
      when 404 then NotFoundError.new(message, **info)
      when 422 then ValidationError.new(message, **info)
      when 429
        after = response['Retry-After']
        RateLimitError.new(message, retry_after: after && after.match?(/\A\d+\z/) ? after.to_f : nil, **info)
      else Error.new(message, **info)
      end
    end

    def build_query(params)
      pairs = []
      params.each do |key, value|
        next if value.nil?

        if value.is_a?(Array)
          value.each { |item| pairs << ["#{key}[]", item.to_s] }
        else
          pairs << [key.to_s, value == true ? 'true' : (value == false ? 'false' : value.to_s)]
        end
      end
      URI.encode_www_form(pairs)
    end

    def delay(retry_after, attempt)
      return [retry_after.to_f, 30.0].min if retry_after && retry_after.match?(/\A\d+(\.\d+)?\z/)

      [0.5 * (2**attempt), 8.0].min
    end

    def normalize(name)
      name.to_s.downcase.gsub(/[^a-z0-9]/, '')
    end
  end
end
