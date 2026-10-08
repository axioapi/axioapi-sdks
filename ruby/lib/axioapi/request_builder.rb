# frozen_string_literal: true

require 'uri'

module AxioAPI
  PreparedRequest = Struct.new(:http_method, :path, :query, :body, keyword_init: true)

  # Splits flat params into path, query and body according to the operation.
  module RequestBuilder
    module_function

    def build(operation, params)
      remaining = params.transform_keys(&:to_s)
      path = fill_path(operation, remaining)
      query = {}
      body = {}
      remaining.each do |name, value|
        next if value.nil?

        (body_param?(operation, name) ? body : query)[name] = value
      end
      PreparedRequest.new(http_method: operation.http_method, path: path, query: query, body: operation.body? && !body.empty? ? body : nil)
    end

    def encode_query(params)
      URI.encode_www_form(query_pairs(params))
    end

    def fill_path(operation, params)
      operation.path.dup.tap do |path|
        operation.path_params.each do |name|
          raise ArgumentError, "Missing path parameter '#{name}' for #{operation.key}" unless params.key?(name)

          path.sub!("{#{name}}", URI.encode_www_form_component(params.delete(name).to_s).gsub('+', '%20'))
        end
      end
    end

    def body_param?(operation, name)
      operation.body? && (operation.body.include?(name) || !operation.query.include?(name))
    end

    def query_pairs(params)
      params.flat_map do |key, value|
        case value
        when nil then []
        when Array then value.map { |item| ["#{key}[]", item.to_s] }
        else [[key.to_s, value.to_s]]
        end
      end
    end
  end
end
