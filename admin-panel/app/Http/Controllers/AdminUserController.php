<?php

namespace App\Http\Controllers;

use App\Models\User;
use App\Services\AdminActivityRecorder;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Illuminate\View\View;

class AdminUserController extends Controller
{
    public function __construct(private readonly AdminActivityRecorder $activities) {}

    public function index(Request $request): View
    {
        $this->authorizeOwner($request);

        return view('admins.index', ['admins' => User::query()->orderBy('name')->get()]);
    }

    public function store(Request $request): RedirectResponse
    {
        $this->authorizeOwner($request);
        $data = $request->validate([
            'name' => ['required', 'string', 'min:2', 'max:80'],
            'email' => ['required', 'email', 'max:255', 'unique:users,email'],
            'role' => ['required', Rule::in(['owner', 'admin', 'moderator'])],
            'password' => ['required', 'string', 'min:12', 'max:72', 'confirmed'],
        ]);
        $admin = User::query()->create($data + ['enabled' => true]);
        $this->activities->record($request, 'admin.created', "Administrateur {$admin->email} créé", 'admin', $admin->id, ['role' => $admin->role]);

        return back()->with('success', 'Compte administrateur créé.');
    }

    public function update(Request $request, User $admin): RedirectResponse
    {
        $this->authorizeOwner($request);
        $data = $request->validate([
            'name' => ['required', 'string', 'min:2', 'max:80'],
            'email' => ['required', 'email', 'max:255', Rule::unique('users', 'email')->ignore($admin)],
            'role' => ['required', Rule::in(['owner', 'admin', 'moderator'])],
            'password' => ['nullable', 'string', 'min:12', 'max:72', 'confirmed'],
        ]);
        if ($admin->isOwner() && $data['role'] !== 'owner' && User::query()->where('role', 'owner')->where('enabled', true)->count() <= 1) {
            return back()->withErrors(['role' => 'Le panneau doit conserver au moins un propriétaire actif.']);
        }
        if (blank($data['password'])) {
            unset($data['password']);
        }
        $admin->update($data);
        $this->activities->record($request, 'admin.updated', "Administrateur {$admin->email} modifié", 'admin', $admin->id, ['role' => $admin->role]);

        return back()->with('success', 'Administrateur mis à jour.');
    }

    public function toggle(Request $request, User $admin): RedirectResponse
    {
        $this->authorizeOwner($request);
        if ($request->user()->is($admin)) {
            return back()->withErrors(['admin' => 'Tu ne peux pas désactiver ton propre compte.']);
        }
        if ($admin->isOwner() && $admin->enabled && User::query()->where('role', 'owner')->where('enabled', true)->count() <= 1) {
            return back()->withErrors(['admin' => 'Le panneau doit conserver au moins un propriétaire actif.']);
        }
        $admin->update(['enabled' => ! $admin->enabled]);
        $this->activities->record($request, 'admin.toggled', "Administrateur {$admin->email} ".($admin->enabled ? 'réactivé' : 'désactivé'), 'admin', $admin->id);

        return back()->with('success', $admin->enabled ? 'Administrateur réactivé.' : 'Administrateur désactivé.');
    }

    private function authorizeOwner(Request $request): void
    {
        abort_unless($request->user()?->isOwner(), 403);
    }
}
