<?php

// Works with composer's autoloader, or standalone from a checkout.
foreach ([__DIR__.'/../vendor/autoload.php', __DIR__.'/../../../vendor/autoload.php'] as $autoload) {
    if (is_file($autoload)) {
        require_once $autoload;
        break;
    }
}
spl_autoload_register(static function (string $class): void {
    if (str_starts_with($class, 'AxioAPI\\') && ! str_starts_with($class, 'AxioAPI\\Tests\\')) {
        $file = __DIR__.'/../src/'.str_replace('\\', '/', substr($class, 8)).'.php';
        if (is_file($file)) {
            require_once $file;
        }
    }
});
