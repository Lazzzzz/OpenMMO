<?php

namespace App\Http\Controllers;

use App\Models\AdminActivity;
use App\Models\GameCharacter;
use App\Models\PlayerAccess;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\View\View;
use Throwable;

class DashboardController extends Controller
{
    public function __invoke(Request $request): View
    {
        $databaseStatus = ['login' => false, 'game' => false];
        $stats = ['players' => 0, 'enabled' => 0, 'characters' => 0, 'pokemon' => 0];

        try {
            DB::connection('openmmo_login')->getPdo();
            $databaseStatus['login'] = true;
            $stats['players'] = PlayerAccess::query()->count();
            $stats['enabled'] = PlayerAccess::query()->where('enabled', true)->count();
        } catch (Throwable) {
            // Keep the panel usable while a server database is offline.
        }

        try {
            DB::connection('openmmo_game')->getPdo();
            $databaseStatus['game'] = true;
            $stats['characters'] = GameCharacter::query()->count();
            $stats['pokemon'] = DB::connection('openmmo_game')->table('pokemon')->count();
        } catch (Throwable) {
            // Keep the panel usable while a server database is offline.
        }

        return view('dashboard', [
            'stats' => $stats,
            'databaseStatus' => $databaseStatus,
            'serviceStatus' => [
                'login' => $this->portIsOpen(config('openmmo.login_host'), config('openmmo.login_port')),
                'game' => $this->portIsOpen(config('openmmo.game_host'), config('openmmo.game_port')),
            ],
            'activities' => AdminActivity::query()->latest()->limit(8)->get(),
        ]);
    }

    private function portIsOpen(string $host, int $port): bool
    {
        $socket = @fsockopen($host, $port, $errorCode, $errorMessage, config('openmmo.status_timeout'));
        if ($socket === false) {
            return false;
        }

        fclose($socket);

        return true;
    }
}
