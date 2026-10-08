# frozen_string_literal: true

require_relative 'axioapi/errors'
require_relative 'axioapi/client'

# Ruby client for the AxioAPI REST API (https://axioapi.com).
#
#   client = AxioAPI::Client.new('ak_...')
#   client.seo.keyword_metrics(keywords: ['api gateway'], country: 'us')
module AxioAPI
  VERSION = '1.0.0'
end
