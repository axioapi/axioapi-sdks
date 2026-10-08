<?php

declare(strict_types=1);

namespace AxioAPI\Http;

use AxioAPI\Exception\ConnectionException;

/** One HTTP round trip with ext-curl; HTTP error statuses are returned, not thrown. */
final class CurlTransport
{
    private const CONNECT_TIMEOUT_SECONDS = 10;

    public function __construct(private readonly float $timeoutSeconds) {}

    /**
     * @param  list<string>  $headers  "Name: value" lines
     *
     * @throws ConnectionException when no response arrived
     */
    public function send(string $method, string $url, array $headers, ?string $payload): HttpResponse
    {
        $handle = curl_init($url);
        curl_setopt_array($handle, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HEADER => true,
            CURLOPT_TIMEOUT_MS => (int) ($this->timeoutSeconds * 1000),
            CURLOPT_CONNECTTIMEOUT => self::CONNECT_TIMEOUT_SECONDS,
        ]);
        if ($payload !== null) {
            curl_setopt($handle, CURLOPT_POSTFIELDS, $payload);
        }
        $raw = curl_exec($handle);
        if ($raw === false) {
            $message = curl_error($handle);
            curl_close($handle);

            throw new ConnectionException($message);
        }
        $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        $headerSize = (int) curl_getinfo($handle, CURLINFO_HEADER_SIZE);
        curl_close($handle);

        return new HttpResponse($status, $this->parseHeaders(substr($raw, 0, $headerSize)), substr($raw, $headerSize));
    }

    /** @return array<string, string> */
    private function parseHeaders(string $block): array
    {
        $blocks = array_values(array_filter(preg_split("/\r\n\r\n/", trim($block)) ?: []));
        $last = $blocks === [] ? '' : (string) end($blocks);
        $headers = [];
        foreach (explode("\r\n", $last) as $line) {
            if (str_contains($line, ':')) {
                [$name, $value] = explode(':', $line, 2);
                $headers[strtolower(trim($name))] = trim($value);
            }
        }

        return $headers;
    }
}
