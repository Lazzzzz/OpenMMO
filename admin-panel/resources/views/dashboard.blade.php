@extends('layouts.app')

@section('title', 'Vue d’ensemble')
@section('heading', 'Vue d’ensemble')

@section('content')
<section class="hero-card">
    <div>
        <span class="kicker">Serveur communautaire</span>
        <h2>Salut {{ auth()->user()->name }},<br>tout est prêt pour jouer.</h2>
        <p>{{ count($onlinePlayers) }} joueur{{ count($onlinePlayers) > 1 ? 's' : '' }} en ligne · {{ $stats['new_players'] }} nouveau{{ $stats['new_players'] > 1 ? 'x' : '' }} compte{{ $stats['new_players'] > 1 ? 's' : '' }} cette semaine.</p>
    </div>
    <div class="hero-orbit" aria-hidden="true"><span></span></div>
</section>

<section class="stat-grid" aria-label="Statistiques">
    <article class="stat-card accent-blue"><span class="stat-icon">◎</span><div><small>Comptes joueurs</small><strong>{{ number_format($stats['players'], 0, ',', ' ') }}</strong><p>{{ $stats['enabled'] }} accès actifs</p></div></article>
    <article class="stat-card accent-purple"><span class="stat-icon">◈</span><div><small>Personnages</small><strong>{{ number_format($stats['characters'], 0, ',', ' ') }}</strong><p>toutes régions</p></div></article>
    <article class="stat-card accent-green"><span class="stat-icon">●</span><div><small>Pokémon</small><strong>{{ number_format($stats['pokemon'], 0, ',', ' ') }}</strong><p>capturés et stockés</p></div></article>
    <article class="stat-card accent-orange"><span class="stat-icon">↗</span><div><small>En ligne</small><strong>{{ count($onlinePlayers) }}</strong><p>{{ $stats['active_characters'] }} actifs sur 24 h</p></div></article>
</section>

<div class="dashboard-grid">
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Infrastructure</span><h3>État du serveur</h3></div><span class="updated">maintenant</span></div>
        <div class="service-list">
            @foreach ([
                ['Connexion', 'Port '.config('openmmo.login_port'), $serviceStatus['login'], $databaseStatus['login']],
                ['Monde de jeu', 'Port '.config('openmmo.game_port'), $serviceStatus['game'], $databaseStatus['game']],
            ] as [$name, $detail, $online, $database])
                <div class="service-row">
                    <span class="service-dot {{ $online ? 'online' : 'offline' }}"></span>
                    <div><strong>{{ $name }}</strong><small>{{ $detail }}</small></div>
                    <div class="service-badges">
                        <span class="badge {{ $online ? 'good' : 'bad' }}">{{ $online ? 'En ligne' : 'Hors ligne' }}</span>
                        <span class="badge {{ $database ? 'good subtle' : 'bad subtle' }}">DB {{ $database ? 'OK' : 'indisponible' }}</span>
                    </div>
                </div>
            @endforeach
        </div>
    </section>

    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Journal</span><h3>Activité récente</h3></div><a href="{{ route('accesses.index') }}">Voir les accès</a></div>
        <div class="activity-list">
            @forelse ($activities as $activity)
                <div class="activity-row"><span class="activity-mark">✓</span><div><strong>{{ $activity->description }}</strong><small>{{ $activity->created_at->diffForHumans() }}</small></div></div>
            @empty
                <div class="empty-state compact"><span>✦</span><p>Les actions d’administration apparaîtront ici.</p></div>
            @endforelse
        </div>
    </section>
</div>

<div class="dashboard-grid dashboard-grid-lower">
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Présence</span><h3>Joueurs connectés</h3></div>@if(auth()->user()->canOperateServer())<a href="{{ route('server.index') }}">Console serveur</a>@endif</div>
        <div class="character-list">
            @forelse($onlinePlayers as $player)
                <a class="character-card" href="{{ route('characters.show', $player['id']) }}"><span class="service-dot online"></span><div><strong>{{ $player['name'] }}</strong><small>Session active maintenant</small></div><span class="table-actions">Gérer →</span></a>
            @empty
                <div class="empty-state compact"><span>○</span><p>Aucun joueur connecté actuellement.</p></div>
            @endforelse
        </div>
    </section>
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Répartition</span><h3>Régions des personnages</h3></div></div>
        @php($regionTotal = max(1, (int) $regions->sum()))
        <div class="metric-bars">
            @foreach([0 => 'Kanto', 1 => 'Hoenn'] as $regionId => $regionName)
                @php($total = (int) ($regions[$regionId] ?? 0))
                <div><span><strong>{{ $regionName }}</strong><small>{{ $total }} personnage{{ $total > 1 ? 's' : '' }}</small></span><i><b style="width: {{ round($total / $regionTotal * 100) }}%"></b></i></div>
            @endforeach
        </div>
    </section>
</div>
@endsection
