<?php

// Builds sdk/spec/operations.json from the live OpenAPI document. Run from the repo root: php sdk/tools/generate_registry.php
require __DIR__.'/../../vendor/autoload.php';
$app = require __DIR__.'/../../bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();

$doc = app(App\ApiHub\OpenApiGenerator::class)->document();
$ops = [];
foreach ($doc['paths'] as $path => $methods) {
    foreach ($methods as $method => $op) {
        $params = ['path' => [], 'query' => [], 'body' => []];
        foreach ($op['parameters'] ?? [] as $p) {
            $params[$p['in'] === 'path' ? 'path' : 'query'][] = $p['name'];
        }
        $schema = $op['requestBody']['content']['application/json']['schema'] ?? null;
        foreach (array_keys((array) ($schema['properties'] ?? [])) as $name) {
            $params['body'][] = $name;
        }
        $tag = $op['tags'][0] ?? 'misc';
        $key = $op['x-capability'] ?? null;
        if ($key === null) {
            $segments = array_values(array_filter(explode('/', substr($path, strlen('/api/v1/'))), fn (string $s): bool => $s !== '' && ! str_starts_with($s, '{')));
            $rest = array_slice($segments, 1);
            $name = $rest === [] ? ($params['path'] === [] ? 'index' : 'show') : strtolower(implode('-', $rest));
            $key = $tag.'.'.preg_replace('/[^a-z0-9]+/', '-', $name);
            if (strtolower($method) !== 'get') {
                $key .= '-'.strtolower($method);
            }
        }
        if (isset($ops[$key])) {
            $key .= '-by-'.implode('-', $params['path']);
        }
        if ($tag === 'openapi') {
            continue;
        }
        $ops[$key] = [
            'method' => strtoupper($method),
            'path' => $path,
            'summary' => $op['summary'] ?? '',
            'cost' => $op['x-credit-cost'] ?? null,
            'path_params' => $params['path'],
            'query' => $params['query'],
            'body' => $params['body'],
            'binary' => isset($op['responses']['200']['content']['image/*']) || isset($op['responses']['200']['content']['audio/*']),
            'auth' => isset($op['security']),
        ];
    }
}
ksort($ops);
file_put_contents(__DIR__.'/../spec/operations.json', json_encode(['version' => $doc['info']['version'], 'operations' => $ops], JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)."\n");
echo count($ops)." operations\n";
