<?php

namespace App\Http\Controllers;

use App\Http\Requests\StorePlayerAccessRequest;
use App\Http\Requests\UpdatePlayerAccessRequest;
use App\Models\AdminActivity;
use App\Models\GameCharacter;
use App\Models\PlayerAccess;
use App\Models\PlayerNote;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\View\View;

class PlayerAccessController extends Controller
{
    public function index(Request $request): View
    {
        $search = $request->string('q')->trim()->toString();
        $accesses = PlayerAccess::query()->search($search)->latest('id')->paginate(15)->withQueryString();
        $characterCounts = GameCharacter::query()
            ->whereIn('user_id', $accesses->pluck('id'))
            ->selectRaw('user_id, COUNT(*) as total')
            ->groupBy('user_id')
            ->pluck('total', 'user_id');

        return view('accesses.index', compact('accesses', 'characterCounts', 'search'));
    }

    public function create(): View
    {
        return view('accesses.create');
    }

    public function store(StorePlayerAccessRequest $request): RedirectResponse
    {
        $data = $request->validated();
        $access = PlayerAccess::query()->create([
            'username' => strtolower($data['username']),
            'display_name' => $data['display_name'],
            'password_hash' => PlayerAccess::passwordHash($data['password']),
            'enabled' => $request->boolean('enabled', true),
            'token_epoch' => 0,
        ]);

        $this->record($request, 'access.created', $access, "Accès {$access->username} créé");

        return redirect()->route('accesses.show', $access)->with('success', 'Accès joueur créé.');
    }

    public function show(PlayerAccess $access): View
    {
        $characters = GameCharacter::query()->where('user_id', $access->id)->latest('last_login')->get();
        $activities = AdminActivity::query()
            ->where('target_type', 'player_access')
            ->where('target_id', (string) $access->id)
            ->latest()
            ->limit(10)
            ->get();
        $notes = PlayerNote::query()
            ->with('author')
            ->where('player_access_id', $access->id)
            ->latest()
            ->limit(20)
            ->get();
        $mutedUntil = DB::connection('openmmo_game')->table('characters')
            ->where('user_id', $access->id)
            ->where('muted_until', '>', now())
            ->max('muted_until');

        return view('accesses.show', compact('access', 'characters', 'activities', 'notes', 'mutedUntil'));
    }

    public function edit(PlayerAccess $access): View
    {
        return view('accesses.edit', compact('access'));
    }

    public function update(UpdatePlayerAccessRequest $request, PlayerAccess $access): RedirectResponse
    {
        $data = $request->validated();
        $passwordChanged = filled($data['password'] ?? null);
        $access->username = strtolower($data['username']);
        $access->display_name = $data['display_name'];

        if ($passwordChanged) {
            $access->password_hash = PlayerAccess::passwordHash($data['password']);
            $access->token_epoch++;
        }

        $access->save();
        $description = $passwordChanged
            ? "Accès {$access->username} et mot de passe modifiés"
            : "Accès {$access->username} modifié";
        $this->record($request, 'access.updated', $access, $description);

        return redirect()->route('accesses.show', $access)->with('success', 'Accès mis à jour.');
    }

    public function toggle(Request $request, PlayerAccess $access): RedirectResponse
    {
        $access->enabled = ! $access->enabled;
        if (! $access->enabled) {
            $access->token_epoch++;
        }
        $access->save();

        $label = $access->enabled ? 'réactivé' : 'désactivé';
        $this->record($request, 'access.toggled', $access, "Accès {$access->username} {$label}");

        return back()->with('success', "Accès {$label}.");
    }

    public function revoke(Request $request, PlayerAccess $access): RedirectResponse
    {
        DB::connection('openmmo_login')->transaction(function () use ($access): void {
            $access->increment('token_epoch');
        });
        $this->record($request, 'access.sessions_revoked', $access, "Sessions de {$access->username} révoquées");

        return back()->with('success', 'Toutes les sessions mémorisées ont été révoquées.');
    }

    private function record(Request $request, string $action, PlayerAccess $access, string $description): void
    {
        AdminActivity::query()->create([
            'user_id' => $request->user()?->id,
            'action' => $action,
            'target_type' => 'player_access',
            'target_id' => (string) $access->id,
            'description' => $description,
            'ip_address' => $request->ip(),
        ]);
    }
}
