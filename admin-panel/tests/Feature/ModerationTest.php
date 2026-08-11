<?php

namespace Tests\Feature;

use App\Models\PlayerAccess;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class ModerationTest extends TestCase
{
    use RefreshDatabase;

    public function test_admin_can_ban_mute_and_note_a_player(): void
    {
        $admin = User::factory()->create(['role' => 'admin']);
        $access = PlayerAccess::query()->create([
            'username' => 'julien', 'display_name' => 'Julien', 'password_hash' => sha1('password'),
            'enabled' => true, 'token_epoch' => 0,
        ]);
        DB::connection('openmmo_game')->table('characters')->insert([
            'id' => 100, 'user_id' => $access->id, 'name' => 'Blue', 'last_login' => now(), 'created_at' => now(),
        ]);

        $this->actingAs($admin)->post("/acces/{$access->id}/bannir", ['hours' => 24, 'reason' => 'Test de modération'])->assertRedirect();
        $this->actingAs($admin)->post("/acces/{$access->id}/mute", ['hours' => 2])->assertRedirect();
        $this->actingAs($admin)->post("/acces/{$access->id}/notes", ['body' => 'Note interne utile'])->assertRedirect();

        $access->refresh();
        $this->assertTrue($access->isBanned());
        $this->assertSame(1, $access->token_epoch);
        $this->assertNotNull(DB::connection('openmmo_game')->table('characters')->where('id', 100)->value('muted_until'));
        $this->assertDatabaseHas('player_notes', ['player_access_id' => $access->id, 'body' => 'Note interne utile']);
    }
}
