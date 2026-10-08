# frozen_string_literal: true

module AxioAPI
  # `client.seo`: method-call access to the operations of one API group.
  class OperationGroup
    def initialize(client, group)
      @client = client
      @group = group
    end

    def method_missing(name, **params)
      key = @client.registry.resolve(@group, name)
      return super unless key

      @client.call(key, **params)
    end

    def respond_to_missing?(name, include_private = false)
      !@client.registry.resolve(@group, name).nil? || super
    end
  end
end
