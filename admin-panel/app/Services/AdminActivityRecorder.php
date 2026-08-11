<?php

namespace App\Services;

use App\Models\AdminActivity;
use Illuminate\Http\Request;

class AdminActivityRecorder
{
    public function record(
        Request $request,
        string $action,
        string $description,
        ?string $targetType = null,
        string|int|null $targetId = null,
        ?array $metadata = null,
    ): AdminActivity {
        return AdminActivity::query()->create([
            'user_id' => $request->user()?->id,
            'action' => $action,
            'target_type' => $targetType,
            'target_id' => $targetId === null ? null : (string) $targetId,
            'description' => $description,
            'metadata' => $metadata,
            'ip_address' => $request->ip(),
        ]);
    }
}
