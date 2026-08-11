<?php

namespace App\Services;

use Illuminate\Http\Client\PendingRequest;
use Illuminate\Support\Facades\Http;
use Throwable;

class GameAdminClient
{
    public function onlinePlayers(): array
    {
        try {
            return $this->requireOnlinePlayers();
        } catch (Throwable) {
            return [];
        }
    }

    public function requireOnlinePlayers(): array
    {
        return $this->client()->get('/players')->throw()->json('players', []);
    }

    public function announce(string $message): void
    {
        $this->client()->withBody($message, 'text/plain')->post('/announce')->throw();
    }

    public function disconnect(int $characterId): bool
    {
        $response = $this->client()->delete('/players?id='.$characterId);

        return $response->successful();
    }

    public function resetCharacter(int $characterId): void
    {
        $this->client()->post('/reset-character?id='.$characterId)->throw();
    }

    private function client(): PendingRequest
    {
        return Http::baseUrl(config('openmmo.admin_url'))
            ->withToken(config('openmmo.admin_token'))
            ->acceptJson()
            ->timeout(3)
            ->retry(2, 100, throw: false);
    }
}
