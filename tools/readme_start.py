"""Writes the "Get started in 3 steps" block of each SDK README (install from GitHub works before registry release). Idempotent."""

import re
from pathlib import Path

root = Path(__file__).resolve().parent.parent
BEGIN, END = "<!-- start:begin -->", "<!-- start:end -->"

KEY_STEP = """### 1. Get an API key

[Create a free account](https://axioapi.com/portal/register), then open [API keys](https://axioapi.com/account/tokens), create a key and copy it. New accounts receive free credits after verification, enough to try every endpoint.

Set it as an environment variable (the SDK reads `AXIOAPI_KEY`):

```bash
export AXIOAPI_KEY=ak_your_key        # macOS / Linux
```

```powershell
$env:AXIOAPI_KEY = "ak_your_key"      # Windows PowerShell
```
"""

langs = {
    "python": {
        "install": """```bash
pip install git+https://github.com/axioapi/axioapi-python.git
```

Requires Python 3.9+. No dependencies. Once released on PyPI: `pip install axioapi`.""",
        "hello": """```python
from axioapi import AxioAPI

client = AxioAPI()                      # reads AXIOAPI_KEY
print(client.account.limits())          # your plan and rate limits
print(client.seo.keyword_metrics(keywords=["api gateway"], country="us"))
```""",
    },
    "node": {
        "install": """```bash
npm install github:axioapi/axioapi-node
```

Requires Node.js 18+. No dependencies, ESM, TypeScript types included. Once released on npm: `npm install axioapi`.""",
        "hello": """```js
import { AxioAPI } from 'axioapi';

const client = new AxioAPI();           // reads AXIOAPI_KEY
console.log(await client.account.limits());
console.log(await client.seo.keywordMetrics({ keywords: ['api gateway'], country: 'us' }));
```""",
    },
    "php": {
        "install": """```bash
composer config repositories.axioapi vcs https://github.com/axioapi/axioapi-php
composer require axioapi/axioapi:dev-main
```

Requires PHP 8.1+ with `ext-curl`. Once released on Packagist: `composer require axioapi/axioapi`.""",
        "hello": """```php
<?php
require 'vendor/autoload.php';

$client = new AxioAPI\\Client();         // reads AXIOAPI_KEY
var_dump($client->account->limits());
var_dump($client->seo->keywordMetrics(['keywords' => ['api gateway'], 'country' => 'us']));
```""",
    },
    "go": {
        "install": """```bash
go get github.com/axioapi/axioapi-go@main
```

Requires Go 1.21+. Standard library only.""",
        "hello": """```go
package main

import (
	"context"
	"fmt"

	axioapi "github.com/axioapi/axioapi-go"
)

func main() {
	client, err := axioapi.New("") // empty reads AXIOAPI_KEY
	if err != nil {
		panic(err)
	}
	limits, err := client.Group("account").Call(context.Background(), "limits", nil)
	fmt.Println(string(limits), err)
}
```""",
    },
    "ruby": {
        "install": """```ruby
# Gemfile
gem 'axioapi', git: 'https://github.com/axioapi/axioapi-ruby.git'
```

Then `bundle install`. Requires Ruby 2.7+. Standard library only. Once released on RubyGems: `gem install axioapi`.""",
        "hello": """```ruby
require 'axioapi'

client = AxioAPI::Client.new            # reads AXIOAPI_KEY
p client.account.limits
p client.seo.keyword_metrics(keywords: ['api gateway'], country: 'us')
```""",
    },
    "java": {
        "install": """With [JitPack](https://jitpack.io) (no account needed):

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>

<dependency>
  <groupId>com.github.axioapi</groupId>
  <artifactId>axioapi-java</artifactId>
  <version>main-SNAPSHOT</version>
</dependency>
```

Or `git clone https://github.com/axioapi/axioapi-java && mvn install`, then use `com.axioapi:axioapi:1.0.0`. Requires Java 11+; depends only on Jackson.""",
        "hello": """```java
import com.axioapi.AxioApi;
import java.util.List;
import java.util.Map;

public class Hello {
    public static void main(String[] args) {
        AxioApi client = AxioApi.builder(null).build();   // null reads AXIOAPI_KEY
        System.out.println(client.group("account").call("limits"));
        System.out.println(client.group("seo").call("keywordMetrics", Map.of("keywords", List.of("api gateway"), "country", "us")));
    }
}
```""",
    },
    "csharp": {
        "install": """```bash
git clone https://github.com/axioapi/axioapi-dotnet.git ../axioapi-dotnet
dotnet add reference ../axioapi-dotnet/src/AxioAPI/AxioAPI.csproj
```

Clone next to your project (not inside it). Requires .NET 6+. No dependencies beyond `System.Text.Json`. Once released on NuGet: `dotnet add package AxioAPI`.""",
        "hello": """```csharp
using AxioAPI;

using var client = new AxioApiClient();   // reads AXIOAPI_KEY
Console.WriteLine(await client.Group("account").CallAsync("limits"));
Console.WriteLine(await client.Group("seo").CallAsync("keywordMetrics", new { keywords = new[] { "api gateway" }, country = "us" }));
```""",
    },
}


def start_block(lang: str) -> str:
    data = langs[lang]
    return f"""{BEGIN}
## Get started in 3 steps

{KEY_STEP}
### 2. Install

{data['install']}

### 3. Make your first call

{data['hello']}

Every endpoint works the same way: `client.<group>.<operation>(params)`. See the examples below and the [full reference](https://axioapi.com/docs).
{END}
"""


for lang in langs:
    path = root / lang / "README.md"
    text = path.read_text(encoding="utf-8")
    if BEGIN in text:
        pattern = re.escape(BEGIN) + r".*?" + re.escape(END) + r"\n"
        text = re.sub(pattern, lambda _: start_block(lang), text, count=1, flags=re.S)
    else:
        # first run: the first fenced block is the old install snippet, replace it
        first = text.index("```")
        close = text.index("```", first + 3) + 3
        after_close = text.index("\n", close) + 1
        text = text[:first] + start_block(lang) + "\n## More examples\n\n" + text[after_close:].lstrip("\n")
    path.write_text(text, encoding="utf-8", newline="\n")
    print("updated", lang)
