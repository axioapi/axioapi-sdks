# frozen_string_literal: true

require 'json'
require_relative 'naming'
require_relative 'operation'

module AxioAPI
  # Every API operation, loaded from operations.json.
  class Registry
    DEFAULT_FILE = File.join(__dir__, 'operations.json')

    def self.load(file = DEFAULT_FILE)
      data = JSON.parse(File.read(file, encoding: 'UTF-8'))['operations']
      new(data.map { |key, entry| Operation.from_hash(key, entry) })
    end

    def initialize(operations)
      @operations = operations.to_h { |operation| [operation.key, operation] }
      @index = {}
      @groups = {}
      @operations.each_key { |key| register(key) }
    end

    def all
      @operations.dup
    end

    def group?(group)
      @groups.key?(Naming.normalize(group))
    end

    # Capability key for a group and operation name in any spelling, or nil.
    def resolve(group, name)
      @index[index_key(group, name)]
    end

    # Looks an operation up by exact key or by any spelling of group.name.
    def find(operation)
      key = operation.to_s
      return @operations[key] if @operations.key?(key)
      return nil unless key.include?('.')

      group, name = key.split('.', 2)
      resolved = resolve(group, name)
      resolved && @operations[resolved]
    end

    private

    def register(key)
      group, name = key.split('.', 2)
      @groups[Naming.normalize(group)] = true
      @index[index_key(group, name)] = key
    end

    def index_key(group, name)
      "#{Naming.normalize(group)}.#{Naming.normalize(name)}"
    end
  end
end
