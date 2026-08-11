<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class ServerControlTest extends TestCase
{
    use RefreshDatabase;

    public function test_admin_can_send_an_announcement_and_restart_a_service(): void
    {
        Http::fake([
            'http://127.0.0.1:7780/announce' => Http::response('', 204),
            'http://127.0.0.1:8081/restart*' => Http::response(['ok' => true]),
        ]);
        $admin = User::factory()->create(['role' => 'admin']);

        $this->actingAs($admin)->post('/serveur/annonce', ['message' => 'Redémarrage dans cinq minutes'])->assertRedirect();
        $this->actingAs($admin)->post('/serveur/redemarrer', ['service' => 'game'])->assertRedirect();

        Http::assertSent(fn ($request) => $request->url() === 'http://127.0.0.1:7780/announce');
        Http::assertSent(fn ($request) => str_contains($request->url(), '/restart?service=game'));
        $this->assertDatabaseHas('admin_activities', ['action' => 'server.announced']);
        $this->assertDatabaseHas('admin_activities', ['action' => 'server.restarted']);
    }

    public function test_moderator_cannot_operate_the_server(): void
    {
        $moderator = User::factory()->create(['role' => 'moderator']);

        $this->actingAs($moderator)->get('/serveur')->assertForbidden();
        $this->actingAs($moderator)->post('/serveur/redemarrer', ['service' => 'game'])->assertForbidden();
    }
}
