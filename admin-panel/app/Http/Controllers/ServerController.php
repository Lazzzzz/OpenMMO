<?php

namespace App\Http\Controllers;

use App\Services\AdminActivityRecorder;
use App\Services\GameAdminClient;
use App\Services\OpsAgentClient;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Validation\Rule;
use Illuminate\View\View;

class ServerController extends Controller
{
    public function __construct(
        private readonly OpsAgentClient $ops,
        private readonly GameAdminClient $game,
        private readonly AdminActivityRecorder $activities,
    ) {}

    public function index(Request $request): View
    {
        $this->authorizeOperator($request);
        $service = $request->string('service')->toString();
        if (! in_array($service, ['login', 'game', 'feed'], true)) {
            $service = 'game';
        }

        return view('server.index', [
            'services' => $this->ops->services(),
            'players' => $this->game->onlinePlayers(),
            'logs' => $this->ops->logs($service),
            'logService' => $service,
            'backups' => $this->ops->backups(),
        ]);
    }

    public function announce(Request $request): RedirectResponse
    {
        $this->authorizeOperator($request);
        $data = $request->validate(['message' => ['required', 'string', 'min:2', 'max:300']]);
        $this->game->announce($data['message']);
        $this->activities->record($request, 'server.announced', 'Annonce globale envoyée', 'server', 'game', ['message' => $data['message']]);

        return back()->with('success', 'Annonce envoyée aux joueurs connectés.');
    }

    public function restart(Request $request): RedirectResponse
    {
        $this->authorizeOperator($request);
        $data = $request->validate(['service' => ['required', Rule::in(['login', 'game', 'feed'])]]);
        $this->ops->restart($data['service']);
        $this->activities->record($request, 'server.restarted', "Service {$data['service']} redémarré", 'server', $data['service']);

        return back()->with('success', 'Service redémarré.');
    }

    public function maintenance(Request $request): RedirectResponse
    {
        $this->authorizeOperator($request);
        $enabled = $request->boolean('enabled');
        $this->ops->maintenance($enabled);
        $this->activities->record($request, 'server.maintenance', 'Maintenance '.($enabled ? 'activée' : 'désactivée'), 'server', 'all');

        return back()->with('success', 'Mode maintenance '.($enabled ? 'activé' : 'désactivé').'.');
    }

    public function backup(Request $request): RedirectResponse
    {
        $this->authorizeOperator($request);
        $files = $this->ops->createBackup();
        $this->activities->record($request, 'server.backup', 'Sauvegarde complète créée', 'server', 'databases', ['files' => $files]);

        return back()->with('success', 'Sauvegarde complète créée.');
    }

    public function download(Request $request, string $backup): Response
    {
        $this->authorizeOperator($request);
        abort_unless((bool) preg_match('/^[a-z]+-\d{8}T\d{6}Z\.sql\.gz$/', $backup), 404);
        $response = $this->ops->download($backup);

        return response($response->body(), 200, [
            'Content-Type' => 'application/gzip',
            'Content-Disposition' => 'attachment; filename="'.$backup.'"',
        ]);
    }

    private function authorizeOperator(Request $request): void
    {
        abort_unless($request->user()?->canOperateServer(), 403);
    }
}
