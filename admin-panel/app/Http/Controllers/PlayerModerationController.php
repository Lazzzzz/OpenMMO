<?php

namespace App\Http\Controllers;

use App\Models\PlayerAccess;
use App\Models\PlayerNote;
use App\Services\AdminActivityRecorder;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class PlayerModerationController extends Controller
{
    public function __construct(private readonly AdminActivityRecorder $activities) {}

    public function ban(Request $request, PlayerAccess $access): RedirectResponse
    {
        $data = $request->validate([
            'hours' => ['required', 'integer', 'min:1', 'max:8760'],
            'reason' => ['required', 'string', 'min:3', 'max:255'],
        ]);
        $access->update([
            'banned_until' => now()->addHours((int) $data['hours']),
            'ban_reason' => $data['reason'],
            'token_epoch' => $access->token_epoch + 1,
        ]);
        $this->activities->record($request, 'moderation.banned', "{$access->username} banni jusqu’au {$access->banned_until->format('d/m/Y H:i')}", 'player_access', $access->id, ['reason' => $data['reason']]);

        return back()->with('success', 'Bannissement appliqué et sessions révoquées.');
    }

    public function unban(Request $request, PlayerAccess $access): RedirectResponse
    {
        $access->update(['banned_until' => null, 'ban_reason' => null]);
        $this->activities->record($request, 'moderation.unbanned', "Bannissement de {$access->username} levé", 'player_access', $access->id);

        return back()->with('success', 'Bannissement levé.');
    }

    public function mute(Request $request, PlayerAccess $access): RedirectResponse
    {
        $data = $request->validate(['hours' => ['required', 'integer', 'min:1', 'max:8760']]);
        $until = now()->addHours((int) $data['hours']);
        DB::connection('openmmo_game')->table('characters')->where('user_id', $access->id)->update(['muted_until' => $until]);
        $this->activities->record($request, 'moderation.muted', "Chat de {$access->username} suspendu jusqu’au {$until->format('d/m/Y H:i')}", 'player_access', $access->id);

        return back()->with('success', 'Mute appliqué à tous les personnages.');
    }

    public function unmute(Request $request, PlayerAccess $access): RedirectResponse
    {
        DB::connection('openmmo_game')->table('characters')->where('user_id', $access->id)->update(['muted_until' => null]);
        $this->activities->record($request, 'moderation.unmuted', "Mute de {$access->username} levé", 'player_access', $access->id);

        return back()->with('success', 'Mute levé.');
    }

    public function note(Request $request, PlayerAccess $access): RedirectResponse
    {
        $data = $request->validate(['body' => ['required', 'string', 'min:2', 'max:2000']]);
        PlayerNote::query()->create(['player_access_id' => $access->id, 'user_id' => $request->user()->id, 'body' => $data['body']]);
        $this->activities->record($request, 'moderation.note', "Note ajoutée à {$access->username}", 'player_access', $access->id);

        return back()->with('success', 'Note interne ajoutée.');
    }
}
