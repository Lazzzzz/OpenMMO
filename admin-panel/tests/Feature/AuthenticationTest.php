<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AuthenticationTest extends TestCase
{
    use RefreshDatabase;

    public function test_guest_is_sent_to_the_admin_login(): void
    {
        $this->get('/')->assertRedirect('/connexion');
        $this->get('/connexion')->assertOk()->assertSee('Connexion administrateur');
    }

    public function test_admin_can_log_in_and_log_out(): void
    {
        $admin = User::factory()->create(['password' => bcrypt('a-secure-password')]);

        $this->post('/connexion', ['email' => $admin->email, 'password' => 'a-secure-password'])
            ->assertRedirect('/');
        $this->assertAuthenticatedAs($admin);

        $this->post('/deconnexion')->assertRedirect('/connexion');
        $this->assertGuest();
    }

    public function test_player_credentials_cannot_authenticate_to_the_panel(): void
    {
        $this->post('/connexion', ['email' => 'player@example.test', 'password' => 'password'])
            ->assertSessionHasErrors('email');
        $this->assertGuest();
    }

    public function test_login_is_rate_limited_after_repeated_failures(): void
    {
        User::factory()->create([
            'email' => 'admin@example.test',
            'password' => bcrypt('a-secure-password'),
        ]);

        foreach (range(1, 5) as $attempt) {
            $this->post('/connexion', [
                'email' => 'admin@example.test',
                'password' => 'wrong-password',
            ])->assertSessionHasErrors('email');
        }

        $this->post('/connexion', [
            'email' => 'admin@example.test',
            'password' => 'wrong-password',
        ])->assertTooManyRequests();
    }
}
