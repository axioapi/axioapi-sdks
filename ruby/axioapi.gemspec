# frozen_string_literal: true

require_relative 'lib/axioapi'

Gem::Specification.new do |spec|
  spec.name          = 'axioapi'
  spec.version       = AxioAPI::VERSION
  spec.authors       = ['AxioAPI']
  spec.summary       = 'Official Ruby SDK for the AxioAPI REST API: temp mail API, SMS OTP API, email validation API, proxy API, SEO API, search API and scraper APIs.'
  spec.description   = 'Official Ruby SDK for the AxioAPI REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API), search API and social scraper APIs. One API key.'
  spec.homepage      = 'https://axioapi.com'
  spec.license       = 'MIT'
  spec.required_ruby_version = '>= 2.7'
  spec.files         = Dir['lib/**/*', 'README.md']
  spec.require_paths = ['lib']
  spec.metadata['documentation_uri'] = 'https://axioapi.com/docs'
end
