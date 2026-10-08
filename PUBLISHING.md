# Publishing the SDKs

Package pages (PyPI, npm, Packagist, pkg.go.dev, RubyGems, Maven Central, NuGet) rank for queries like "temp mail api", "backlink api" and "seo api" and give real backlinks to axioapi.com, so publish all of them. Nothing here is published yet.

Before the first release: check that the names are free, bump the version in each manifest, run `bash sdk/tools/test_all.sh`, and regenerate the registry (`php sdk/tools/generate_registry.php && python sdk/tools/sync_spec.py`) so every package knows the current endpoints.

| Registry | Name | Command | Notes |
|---|---|---|---|
| PyPI | `axioapi` | `cd sdk/python && python -m build && twine upload dist/*` | `pip install build twine` first |
| npm | `axioapi` | `cd sdk/node && npm publish --access public` | |
| Packagist | `axioapi/axioapi` | Submit the repo URL at packagist.org | Packagist reads `composer.json` at the **repo root**: publish `sdk/php` as its own repo |
| Go | `github.com/axioapi/axioapi-go` | Push `sdk/go` as the repo root and tag `v1.0.0` | The module path must match the repo URL. Change it in `go.mod` and the README if you use another org |
| RubyGems | `axioapi` | `cd sdk/ruby && gem build axioapi.gemspec && gem push axioapi-1.0.0.gem` | |
| Maven Central | `com.axioapi:axioapi` | Follow the Sonatype Central publishing guide | Needs a verified namespace (`com.axioapi` requires owning axioapi.com) and GPG signing |
| NuGet | `AxioAPI` | `cd sdk/csharp/src/AxioAPI && dotnet pack -c Release && dotnet nuget push bin/Release/*.nupkg` | |

Recommended layout: one repository per language (`axioapi-python`, `axioapi-node`, `axioapi-php`, `axioapi-go`, `axioapi-ruby`, `axioapi-java`, `axioapi-dotnet`) mirrored from this folder, each with a GitHub description and topics such as `api`, `seo-api`, `backlink-api`, `temp-mail-api`, `sms-verification-api`. Add the repo links to the site footer and to the docs page once they exist.

Updating after an API change: run the two commands above, bump versions, release.

## Repository settings (GitHub SEO)

Repo name pattern: `axioapi-python`, `axioapi-node`, `axioapi-php`, `axioapi-go`, `axioapi-ruby`, `axioapi-java`, `axioapi-dotnet` under the `axioapi` organisation. Set the website field to `https://axioapi.com` on every repo and pin them on the organisation page.

Description (under 350 characters, keep the keywords): `Official <Language> SDK for the AxioAPI REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key.`

Topics: `api`, `api-client`, `sdk`, `seo-api`, `backlink-api`, `keyword-api`, `temp-mail-api`, `sms-verification-api`, `otp-api`, `email-validation-api`, `proxy-api`, `scraper-api`, plus the language name.

The README H1 and the "What you can build" table are already written for search: keep the headings when you edit. Run `python sdk/tools/seo_readmes.py` after changing any README to keep the shared sections identical across SDKs.
