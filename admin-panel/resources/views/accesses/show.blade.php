@extends('layouts.app')

@section('title', $access->display_name)
@section('heading', 'Fiche joueur')

@section('content')
<div class="profile-head">
    <div class="profile-identity"><span class="avatar xl">{{ strtoupper(substr($access->display_name, 0, 1)) }}</span><div><a class="back-link" href="{{ route('accesses.index') }}">← Tous les accès</a><h2>{{ $access->display_name }}</h2><p><code>{{ $access->username }}</code> · compte #{{ $access->id }}</p></div></div>
    <div class="profile-actions">
        @if($access->isBanned())<span class="badge large bad">Banni jusqu’au {{ $access->banned_until->format('d/m H:i') }}</span>
        @else<span class="badge large {{ $access->enabled ? 'good' : 'bad' }}">{{ $access->enabled ? 'Accès actif' : 'Accès suspendu' }}</span>@endif
        @if($mutedUntil)<span class="badge large warning">Chat suspendu</span>@endif
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
            @if($access->ban_reason)<div><dt>Motif du bannissement</dt><dd>{{ $access->ban_reason }}</dd></div>@endif
        </dl>
        <div class="danger-actions">
            <form method="POST" action="{{ route('accesses.revoke', $access) }}" data-confirm="Révoquer toutes les sessions enregistrées de ce joueur ?">@csrf<button class="button secondary" type="submit">Révoquer les sessions</button></form>
            <form method="POST" action="{{ route('accesses.toggle', $access) }}" data-confirm="{{ $access->enabled ? 'Suspendre cet accès ?' : 'Réactiver cet accès ?' }}">@csrf @method('PATCH')<button class="button {{ $access->enabled ? 'danger' : 'success' }}" type="submit">{{ $access->enabled ? 'Suspendre l’accès' : 'Réactiver l’accès' }}</button></form>
        </div>
    </section>

    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Jeu</span><h3>Personnages</h3></div></div>
        <div class="character-list">
            @forelse($characters as $character)
                <a class="character-card" href="{{ route('characters.show', $character) }}"><span class="character-level">{{ $character->position_region_id == 0 ? 'K' : 'H' }}</span><div><strong>{{ $character->name }}</strong><small>Dernière connexion {{ $character->last_login?->diffForHumans() ?? 'inconnue' }}</small></div><span class="money">{{ number_format($character->money, 0, ',', ' ') }} ₽ · Gérer →</span></a>
            @empty
                <div class="empty-state compact"><span>◇</span><p>Aucun personnage créé pour ce compte.</p></div>
            @endforelse
        </div>
    </section>
</div>

<div class="dashboard-grid dashboard-grid-lower">
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Modération</span><h3>Sanctions temporaires</h3></div></div>
        @if($access->isBanned())
            <form method="POST" action="{{ route('accesses.unban', $access) }}" data-confirm="Lever le bannissement ?">@csrf @method('DELETE')<button class="button success" type="submit">Lever le bannissement</button></form>
        @else
            <form class="inline-form" method="POST" action="{{ route('accesses.ban', $access) }}" data-confirm="Bannir temporairement ce joueur et révoquer ses sessions ?">@csrf
                <label class="field"><span>Durée en heures</span><input type="number" name="hours" min="1" max="8760" value="24" required></label>
                <label class="field grow"><span>Motif</span><input name="reason" maxlength="255" placeholder="Motif interne" required></label>
                <button class="button danger" type="submit">Bannir</button>
            </form>
        @endif
        <div class="separator"></div>
        @if($mutedUntil)
            <p class="muted-copy">Chat suspendu jusqu’au {{ \Illuminate\Support\Carbon::parse($mutedUntil)->format('d/m/Y à H:i') }}.</p>
            <form method="POST" action="{{ route('accesses.unmute', $access) }}">@csrf @method('DELETE')<button class="button success" type="submit">Rendre le chat</button></form>
        @else
            <form class="inline-form" method="POST" action="{{ route('accesses.mute', $access) }}">@csrf
                <label class="field"><span>Mute (heures)</span><input type="number" name="hours" min="1" max="8760" value="1" required></label>
                <button class="button secondary" type="submit">Suspendre le chat</button>
            </form>
        @endif
    </section>

    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Équipe</span><h3>Notes internes</h3></div></div>
        <form method="POST" action="{{ route('accesses.notes.store', $access) }}">@csrf<label class="field"><span>Nouvelle note</span><textarea name="body" rows="3" maxlength="2000" placeholder="Visible uniquement par l’équipe…" required></textarea></label><div class="form-actions compact"><button class="button primary" type="submit">Ajouter la note</button></div></form>
        <div class="activity-list">
            @foreach($notes as $note)<div class="activity-row"><span class="activity-mark">✎</span><div><strong>{{ $note->body }}</strong><small>{{ $note->author?->name ?? 'Compte supprimé' }} · {{ $note->created_at->format('d/m/Y H:i') }}</small></div></div>@endforeach
        </div>
    </section>
</div>

<section class="panel timeline-panel">
    <div class="panel-head"><div><span class="eyebrow">Audit</span><h3>Historique administratif</h3></div><a href="{{ route('activities.index') }}">Journal complet</a></div>
    <div class="activity-list">
        @forelse($activities as $activity)<div class="activity-row"><span class="activity-mark">✓</span><div><strong>{{ $activity->description }}</strong><small>{{ $activity->created_at->format('d/m/Y à H:i') }} · {{ $activity->ip_address }}</small></div></div>@empty<div class="empty-state compact"><p>Aucune action enregistrée.</p></div>@endforelse
    </div>
</section>
@endsection
