# frozen_string_literal: true

module AxioAPI
  module Naming
    # Folds snake_case, camelCase and kebab-case to one comparable form.
    def self.normalize(name)
      name.to_s.downcase.gsub(/[^a-z0-9]/, '')
    end
  end
end
