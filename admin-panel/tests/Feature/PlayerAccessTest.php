<?php

namespace Tests\Feature;

use App\Models\PlayerAccess;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class PlayerAccessTest extends TestCase
{
    use RefreshDatabase;

    public function test_admin_can_create_a_game_access(): void
    {
        $this->actingAs(User::factory()->create())
            ->post('/acces', [
                'username' => 'Julien2001',
                'display_name' => 'Julien',
                'password' => 'very-secret',
                'password_confirmation' => 'very-secret',
                'enabled' => '1',
            ])
            ->assertRedirect();

        $this->assertDatabaseHas('users', [
            'username' => 'julien2001',
            'display_name' => 'Julien',
            'password_hash' => sha1('very-secret'),
            'enabled' => true,
        ], 'openmmo_login');
        $this->assertDatabaseHas('admin_activities', ['action' => 'access.created']);
    }

    public function test_disabling_an_access_revokes_remembered_sessions(): void
    {
        $admin = User::factory()->create();
        $access = PlayerAccess::query()->create([
            'username' => 'lazar',
            'display_name' => 'Lazar',
            'password_hash' => sha1('password'),
            'enabled' => true,
            'token_epoch' => 2,
        ]);

        $this->actingAs($admin)->patch("/acces/{$access->id}/etat")->assertRedirect();

        $access->refresh();
        $this->assertFalse($access->enabled);
        $this->assertSame(3, $access->token_epoch);
    }

    public function test_resetting_password_hashes_it_for_the_game_protocol(): void
    {
        $admin = User::factory()->create();
        $access = PlayerAccess::query()->create([
            'username' => 'lazar',
            'display_name' => 'Lazar',
            'password_hash' => sha1('old-password'),
            'enabled' => true,
            'token_epoch' => 0,
        ]);

        $this->actingAs($admin)->put("/acces/{$access->id}", [
            'username' => 'lazar',
            'display_name' => 'Lazar',
            'password' => 'new-password',
            'password_confirmation' => 'new-password',
        ])->assertRedirect("/acces/{$access->id}");

        $access->refresh();
        $this->assertSame(sha1('new-password'), $access->password_hash);
        $this->assertSame(1, $access->token_epoch);
    }

    public function test_guest_cannot_manage_player_accesses(): void
    {
        $this->get('/acces')->assertRedirect('/connexion');
        $this->post('/acces', [])->assertRedirect('/connexion');
    }
}
