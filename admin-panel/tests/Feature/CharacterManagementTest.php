<?php

namespace Tests\Feature;

use App\Models\GameCharacter;
use App\Models\PlayerAccess;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class CharacterManagementTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Http::fake([
            'http://127.0.0.1:7780/players' => Http::response(['players' => []]),
            'http://127.0.0.1:7780/reset-character*' => Http::response('', 204),
            'http://127.0.0.1:7780/pokemon*' => Http::response('', 204),
        ]);
    }

    public function test_admin_can_edit_an_offline_character_and_its_inventory(): void
    {
        [$admin, $character] = $this->records();

        $this->actingAs($admin)->put("/personnages/{$character->id}", ['name' => 'Red', 'money' => 45000])->assertRedirect();
        $this->actingAs($admin)->put("/personnages/{$character->id}/inventaire", ['item_id' => 12, 'quantity' => 5])->assertRedirect();

        $this->assertDatabaseHas('characters', ['id' => $character->id, 'name' => 'Red', 'money' => 45000], 'openmmo_game');
        $this->assertDatabaseHas('character_items', ['character_id' => $character->id, 'item_id' => 12, 'quantity' => 5], 'openmmo_game');
    }

    public function test_progress_reset_creates_a_restorable_snapshot(): void
    {
        [$admin, $character] = $this->records();

        $this->actingAs($admin)->post("/personnages/{$character->id}/reset")->assertRedirect();

        $this->assertDatabaseHas('character_snapshots', ['character_id' => $character->id, 'reason' => 'Avant remise à zéro']);
        Http::assertSent(fn ($request) => str_contains($request->url(), '/reset-character?id='.$character->id));
    }

    public function test_admin_can_give_a_pokemon_with_an_automatic_snapshot(): void
    {
        [$admin, $character] = $this->records();

        $this->actingAs($admin)->post("/personnages/{$character->id}/pokemon", [
            'dex_id' => 25,
            'pokemon_level' => 12,
            'container' => 'PC',
            'nickname' => 'Sparky',
            'is_shiny' => '1',
        ])->assertRedirect();

        $this->assertDatabaseHas('character_snapshots', ['character_id' => $character->id, 'reason' => 'Avant ajout d’un Pokémon']);
        Http::assertSent(fn ($request) => $request->method() === 'POST'
            && str_contains($request->url(), '/pokemon?')
            && str_contains($request->url(), 'dexId=25')
            && str_contains($request->url(), 'container=PC'));
    }

    public function test_admin_can_delete_an_owned_pokemon_with_an_automatic_snapshot(): void
    {
        [$admin, $character] = $this->records();
        DB::connection('openmmo_game')->table('pokemon')->insert([
            'id' => 300,
            'owner_id' => $character->id,
            'dex_id' => 7,
            'ot' => 'Leaf',
            'nickname' => 'Carapuce',
            'caught_at' => now(),
        ]);

        $this->actingAs($admin)->delete("/personnages/{$character->id}/pokemon/300")->assertRedirect();

        $this->assertDatabaseHas('character_snapshots', ['character_id' => $character->id, 'reason' => 'Avant suppression d’un Pokémon']);
        Http::assertSent(fn ($request) => $request->method() === 'DELETE'
            && str_contains($request->url(), '/pokemon?')
            && str_contains($request->url(), 'id=300'));
    }

    private function records(): array
    {
        $admin = User::factory()->create(['role' => 'admin']);
        $access = PlayerAccess::query()->create([
            'username' => 'lazar', 'display_name' => 'Lazar', 'password_hash' => sha1('password'),
            'enabled' => true, 'token_epoch' => 0,
        ]);
        DB::connection('openmmo_game')->table('characters')->insert([
            'id' => 200, 'user_id' => $access->id, 'name' => 'Leaf', 'last_login' => now(), 'created_at' => now(), 'money' => 30000,
        ]);

        return [$admin, GameCharacter::query()->findOrFail(200)];
    }
}
