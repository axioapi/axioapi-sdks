<?php

declare(strict_types=1);

namespace AxioAPI\Exception;

/** The request never produced an HTTP response (DNS, TLS, timeout). */
class ConnectionException extends AxioAPIException {}
