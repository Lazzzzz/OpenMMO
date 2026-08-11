<?php

namespace App\Http\Controllers;

use App\Services\AdminActivityRecorder;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rules\Password;
use Illuminate\View\View;

class ProfileController extends Controller
{
    public function __construct(private readonly AdminActivityRecorder $activities) {}

    public function edit(Request $request): View
    {
        return view('profile.edit', ['admin' => $request->user()]);
    }

    public function update(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'min:2', 'max:80'],
            'current_password' => ['required', 'current_password'],
            'password' => ['nullable', 'confirmed', Password::min(12)],
        ]);
        $updates = ['name' => $data['name']];
        if (filled($data['password'])) {
            $updates['password'] = Hash::make($data['password']);
        }
        $request->user()->update($updates);
        $this->activities->record($request, 'profile.updated', 'Profil administrateur mis à jour', 'admin', $request->user()->id);

        return back()->with('success', 'Profil mis à jour.');
    }
}
