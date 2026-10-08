# frozen_string_literal: true

module AxioAPI
  Operation = Struct.new(:key, :http_method, :path, :summary, :cost, :path_params, :query, :body, :binary, keyword_init: true) do
    def self.from_hash(key, data)
      new(
        key: key, http_method: data['method'], path: data['path'], summary: data['summary'].to_s, cost: data['cost'],
        path_params: data['path_params'] || [], query: data['query'] || [], body: data['body'] || [],
        binary: data['binary'] || false
      )
    end

    def body?
      !%w[GET DELETE HEAD].include?(http_method)
    end
  end
end
