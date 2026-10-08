# frozen_string_literal: true

require_relative 'lib/axioapi'

Gem::Specification.new do |spec|
  spec.name          = 'axioapi'
  spec.version       = AxioAPI::VERSION
  spec.authors       = ['AxioAPI']
  spec.summary       = 'Ruby client for the AxioAPI REST API: temp mail, SMS and OTP, email validation, proxies, SEO and backlink data.'
  spec.description   = 'One API key for temp mail API, receive SMS API, OTP API, email validation API, proxy API, SEO API, backlink API and social scraper APIs.'
  spec.homepage      = 'https://axioapi.com'
  spec.license       = 'MIT'
  spec.required_ruby_version = '>= 2.7'
  spec.files         = Dir['lib/**/*', 'README.md']
  spec.require_paths = ['lib']
  spec.metadata['documentation_uri'] = 'https://axioapi.com/docs'
end
