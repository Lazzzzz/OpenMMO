<?php

namespace App\Services;

use Illuminate\Http\Client\PendingRequest;
use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;
use Throwable;

class OpsAgentClient
{
    public function services(): array
    {
        try {
            return $this->client()->get('/services')->throw()->json('services', []);
        } catch (Throwable) {
            return [];
        }
    }

    public function logs(string $service, int $tail = 150): string
    {
        return (string) $this->client()->get('/logs', compact('service', 'tail'))->throw()->json('lines', '');
    }

    public function restart(string $service): void
    {
        $this->client()->post('/restart?service='.$service)->throw();
    }

    public function maintenance(bool $enabled): void
    {
        $this->client()->post('/maintenance?enabled='.($enabled ? '1' : '0'))->throw();
    }

    public function createBackup(): array
    {
        return $this->client()->post('/backups')->throw()->json('files', []);
    }

    public function backups(): array
    {
        try {
            return $this->client()->get('/backups')->throw()->json('backups', []);
        } catch (Throwable) {
            return [];
        }
    }

    public function download(string $name): Response
    {
        return $this->client()->get('/backups/'.$name)->throw();
    }

    private function client(): PendingRequest
    {
        return Http::baseUrl(config('openmmo.ops_url'))
            ->withToken(config('openmmo.ops_token'))
            ->acceptJson()
            ->timeout(130);
    }
}
