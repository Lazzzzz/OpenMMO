@extends('layouts.app')

@section('title', $access->display_name)
@section('heading', 'Fiche joueur')

@section('content')
<div class="profile-head">
    <div class="profile-identity"><span class="avatar xl">{{ strtoupper(substr($access->display_name, 0, 1)) }}</span><div><a class="back-link" href="{{ route('accesses.index') }}">← Tous les accès</a><h2>{{ $access->display_name }}</h2><p><code>{{ $access->username }}</code> · compte #{{ $access->id }}</p></div></div>
    <div class="profile-actions">
        <span class="badge large {{ $access->enabled ? 'good' : 'bad' }}">{{ $access->enabled ? 'Accès actif' : 'Accès suspendu' }}</span>
        <a class="button secondary" href="{{ route('accesses.edit', $access) }}">Modifier</a>
    </div>
</div>

<div class="detail-grid">
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Compte</span><h3>Contrôles d’accès</h3></div></div>
        <dl class="detail-list">
            <div><dt>Création</dt><dd>{{ $access->created_at?->format('d/m/Y à H:i') ?? '—' }}</dd></div>
            <div><dt>Sessions révoquées</dt><dd>{{ $access->token_epoch }} fois</dd></div>
            <div><dt>Personnages</dt><dd>{{ $characters->count() }}</dd></div>
        </dl>
        <div class="danger-actions">
            <form method="POST" action="{{ route('accesses.revoke', $access) }}" data-confirm="Révoquer toutes les sessions enregistrées de ce joueur ?">
                @csrf <button class="button secondary" type="submit">Révoquer les sessions</button>
            </form>
            <form method="POST" action="{{ route('accesses.toggle', $access) }}" data-confirm="{{ $access->enabled ? 'Suspendre cet accès ? Le joueur ne pourra plus se connecter.' : 'Réactiver cet accès ?' }}">
                @csrf @method('PATCH')
                <button class="button {{ $access->enabled ? 'danger' : 'success' }}" type="submit">{{ $access->enabled ? 'Suspendre l’accès' : 'Réactiver l’accès' }}</button>
            </form>
        </div>
    </section>

    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Jeu</span><h3>Personnages</h3></div></div>
        <div class="character-list">
            @forelse($characters as $character)
                <article class="character-card"><span class="character-level">{{ $character->position_region_id == 0 ? 'K' : 'H' }}</span><div><strong>{{ $character->name }}</strong><small>Dernière connexion {{ $character->last_login?->diffForHumans() ?? 'inconnue' }}</small></div><span class="money">{{ number_format($character->money, 0, ',', ' ') }} ₽</span></article>
            @empty
                <div class="empty-state compact"><span>◇</span><p>Aucun personnage créé pour ce compte.</p></div>
            @endforelse
        </div>
    </section>
</div>

<section class="panel timeline-panel">
    <div class="panel-head"><div><span class="eyebrow">Audit</span><h3>Historique administratif</h3></div></div>
    <div class="activity-list">
        @forelse($activities as $activity)
            <div class="activity-row"><span class="activity-mark">✓</span><div><strong>{{ $activity->description }}</strong><small>{{ $activity->created_at->format('d/m/Y à H:i') }} · {{ $activity->ip_address }}</small></div></div>
        @empty
            <div class="empty-state compact"><p>Aucune action enregistrée.</p></div>
        @endforelse
    </div>
</section>
@endsection
