<?php
require __DIR__.'/tests/bootstrap.php';

$client = new AxioAPI\Client();         // reads AXIOAPI_KEY
var_dump($client->account->limits());
var_dump($client->seo->keywordMetrics(['keywords' => ['api gateway'], 'country' => 'us']));
