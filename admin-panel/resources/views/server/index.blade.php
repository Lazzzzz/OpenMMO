@extends('layouts.app')

@section('title', 'Serveur')
@section('heading', 'Serveur & exploitation')

@section('content')
<div class="page-intro"><div><h2>Console opérationnelle</h2><p>Surveille les services, communique avec les joueurs et protège les données.</p></div><form method="POST" action="{{ route('server.backup') }}">@csrf<button class="button primary" type="submit">Créer une sauvegarde</button></form></div>

<section class="service-command-grid">
    @foreach(['login' => ['Connexion', 'Port 2106'], 'game' => ['Monde de jeu', 'Port 7777'], 'feed' => ['Téléchargements', 'HTTPS']] as $key => [$label, $detail])
        @php($state = $services[$key] ?? ['running' => false, 'status' => 'indisponible'])
        <article class="panel service-command"><span class="service-dot {{ $state['running'] ? 'online' : 'offline' }}"></span><div class="grow"><strong>{{ $label }}</strong><small>{{ $detail }} · {{ $state['status'] }}</small></div><form method="POST" action="{{ route('server.restart') }}" data-confirm="Redémarrer {{ $label }} ? Les connexions actives peuvent être interrompues.">@csrf<input type="hidden" name="service" value="{{ $key }}"><button class="button secondary" type="submit">Redémarrer</button></form></article>
    @endforeach
</section>

<div class="dashboard-grid dashboard-grid-lower">
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Communication</span><h3>Annonce globale</h3></div><span class="badge good">{{ count($players) }} en ligne</span></div>
        <form method="POST" action="{{ route('server.announce') }}">@csrf<label class="field"><span>Message envoyé dans le chat du jeu</span><textarea name="message" maxlength="300" rows="4" required placeholder="Le serveur redémarrera dans 5 minutes…"></textarea></label><div class="form-actions compact"><button class="button primary" type="submit">Envoyer à tout le monde</button></div></form>
        <div class="online-pills">@forelse($players as $player)<a href="{{ route('characters.show', $player['id']) }}"><i></i>{{ $player['name'] }}</a>@empty<span class="muted-copy">Aucun joueur connecté.</span>@endforelse</div>
    </section>
    <section class="panel danger-panel">
        <div class="panel-head"><div><span class="eyebrow">Interruption contrôlée</span><h3>Mode maintenance</h3></div></div>
        <p>Coupe ou relance ensemble les connexions et le monde de jeu. Le panneau reste disponible.</p>
        <div class="danger-actions"><form method="POST" action="{{ route('server.maintenance') }}" data-confirm="Activer la maintenance et déconnecter les joueurs ?" data-confirm-text="MAINTENANCE">@csrf<input type="hidden" name="enabled" value="1"><button class="button danger" type="submit">Activer la maintenance</button></form><form method="POST" action="{{ route('server.maintenance') }}">@csrf<input type="hidden" name="enabled" value="0"><button class="button success" type="submit">Remettre en ligne</button></form></div>
    </section>
</div>

<section class="panel log-panel timeline-panel">
    <div class="panel-head"><div><span class="eyebrow">Diagnostic</span><h3>Logs récents</h3></div><nav class="log-tabs">@foreach(['game' => 'Jeu', 'login' => 'Connexion', 'feed' => 'Web'] as $key => $label)<a class="{{ $logService === $key ? 'active' : '' }}" href="{{ route('server.index', ['service' => $key]) }}">{{ $label }}</a>@endforeach</nav></div>
    <pre>{{ $logs ?: 'Aucun log disponible.' }}</pre>
</section>

<section class="panel table-panel timeline-panel">
    <div class="panel-head panel-head-padded"><div><span class="eyebrow">Données</span><h3>Sauvegardes PostgreSQL</h3></div><span class="updated">14 versions conservées</span></div>
    <div class="backup-list">@forelse($backups as $backup)<div><span class="activity-mark">⇩</span><div class="grow"><strong>{{ $backup['name'] }}</strong><small>{{ number_format($backup['size'] / 1024 / 1024, 2, ',', ' ') }} Mo · {{ \Illuminate\Support\Carbon::parse($backup['created_at'])->format('d/m/Y H:i') }}</small></div><a class="button secondary" href="{{ route('server.backups.download', $backup['name']) }}">Télécharger</a></div>@empty<div class="empty-state compact"><p>Aucune sauvegarde créée depuis cette console.</p></div>@endforelse</div>
</section>
@endsection
