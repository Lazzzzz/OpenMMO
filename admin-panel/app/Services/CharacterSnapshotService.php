<?php

namespace App\Services;

use App\Models\CharacterSnapshot;
use App\Models\GameCharacter;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

class CharacterSnapshotService
{
    private const RELATED_TABLES = [
        'pokemon', 'character_items', 'character_flags', 'character_vars', 'character_skins',
    ];

    public function capture(GameCharacter $character, string $reason, ?int $adminId): CharacterSnapshot
    {
        $database = DB::connection('openmmo_game');
        $payload = ['character' => $database->table('characters')->where('id', $character->id)->first()];

        foreach (self::RELATED_TABLES as $table) {
            if (Schema::connection('openmmo_game')->hasTable($table)) {
                $foreignKey = $table === 'pokemon' ? 'owner_id' : 'character_id';
                $payload[$table] = $database->table($table)->where($foreignKey, $character->id)->get();
            }
        }

        return CharacterSnapshot::query()->create([
            'character_id' => $character->id,
            'user_id' => $adminId,
            'reason' => $reason,
            'payload' => json_decode(json_encode($payload, JSON_THROW_ON_ERROR), true, 512, JSON_THROW_ON_ERROR),
        ]);
    }

    public function restore(CharacterSnapshot $snapshot): void
    {
        $database = DB::connection('openmmo_game');
        $payload = $snapshot->payload;

        $database->transaction(function () use ($database, $payload, $snapshot): void {
            foreach (array_reverse(self::RELATED_TABLES) as $table) {
                if (! Schema::connection('openmmo_game')->hasTable($table)) {
                    continue;
                }
                $foreignKey = $table === 'pokemon' ? 'owner_id' : 'character_id';
                $database->table($table)->where($foreignKey, $snapshot->character_id)->delete();
            }

            $character = $payload['character'];
            unset($character['id']);
            $database->table('characters')->where('id', $snapshot->character_id)->update($character);

            foreach (self::RELATED_TABLES as $table) {
                $rows = $payload[$table] ?? [];
                if ($rows !== [] && Schema::connection('openmmo_game')->hasTable($table)) {
                    $database->table($table)->insert($rows);
                }
            }
        });

        $snapshot->update(['restored_at' => now()]);
    }
}
