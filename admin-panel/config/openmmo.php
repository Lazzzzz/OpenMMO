<?php

return [
    'login_host' => env('OPENMMO_LOGIN_HOST', '127.0.0.1'),
    'login_port' => (int) env('OPENMMO_LOGIN_PORT', 2106),
    'game_host' => env('OPENMMO_GAME_HOST', '127.0.0.1'),
    'game_port' => (int) env('OPENMMO_GAME_PORT', 7777),
    'status_timeout' => (float) env('OPENMMO_STATUS_TIMEOUT', 0.35),
    'admin_url' => env('OPENMMO_ADMIN_URL', 'http://127.0.0.1:7780'),
    'admin_token' => env('OPENMMO_ADMIN_TOKEN', 'dev-only-admin-token-change-me-123456'),
    'ops_url' => env('OPENMMO_OPS_URL', 'http://127.0.0.1:8081'),
    'ops_token' => env('OPENMMO_OPS_TOKEN', 'dev-only-ops-token-change-me-12345678'),
];
