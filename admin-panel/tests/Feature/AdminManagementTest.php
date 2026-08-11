<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AdminManagementTest extends TestCase
{
    use RefreshDatabase;

    public function test_owner_can_create_an_administrator(): void
    {
        $owner = User::factory()->create(['role' => 'owner', 'enabled' => true]);

        $this->actingAs($owner)->post('/equipe', [
            'name' => 'Julien',
            'email' => 'julien@example.test',
            'role' => 'moderator',
            'password' => 'a-secure-password',
            'password_confirmation' => 'a-secure-password',
        ])->assertRedirect();

        $this->assertDatabaseHas('users', ['email' => 'julien@example.test', 'role' => 'moderator', 'enabled' => true]);
        $this->assertDatabaseHas('admin_activities', ['action' => 'admin.created']);
    }

    public function test_non_owner_cannot_manage_the_team(): void
    {
        $admin = User::factory()->create(['role' => 'admin', 'enabled' => true]);

        $this->actingAs($admin)->get('/equipe')->assertForbidden();
        $this->actingAs($admin)->post('/equipe', [])->assertForbidden();
    }

    public function test_disabled_admin_cannot_log_in(): void
    {
        $admin = User::factory()->create(['enabled' => false, 'password' => bcrypt('a-secure-password')]);

        $this->post('/connexion', ['email' => $admin->email, 'password' => 'a-secure-password'])
            ->assertSessionHasErrors('email');
        $this->assertGuest();
    }
}
