@extends('layouts.app')

@section('title', $character->name)
@section('heading', 'Personnage')

@section('content')
<div class="profile-head">
    <div class="profile-identity"><span class="avatar xl">{{ strtoupper(substr($character->name, 0, 1)) }}</span><div><a class="back-link" href="{{ route('accesses.show', $access) }}">← {{ $access->display_name }}</a><h2>{{ $character->name }}</h2><p>Personnage #{{ $character->id }} · {{ $character->position_region_id === 0 ? 'Kanto' : 'Hoenn' }}</p></div></div>
    <div class="profile-actions"><span class="badge large {{ $online ? 'good' : 'subtle' }}">{{ $online ? 'En ligne' : 'Hors ligne' }}</span>@if($online && auth()->user()->canOperateServer())<form method="POST" action="{{ route('characters.disconnect', $character) }}" data-confirm="Déconnecter ce joueur ?">@csrf<button class="button danger" type="submit">Déconnecter</button></form>@endif</div>
</div>

@if($online)<div class="flash warning">Les données sont en lecture seule pendant que le personnage est connecté. Déconnecte-le avant une modification.</div>@endif

<div class="detail-grid">
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Identité</span><h3>Informations principales</h3></div></div>
        <form method="POST" action="{{ route('characters.update', $character) }}">@csrf @method('PUT')
            <div class="form-grid compact-grid">
                <label class="field"><span>Nom</span><input name="name" value="{{ old('name', $character->name) }}" maxlength="32" required {{ $online ? 'disabled' : '' }}></label>
                <label class="field"><span>Argent</span><input type="number" name="money" value="{{ old('money', $character->money) }}" min="0" max="2000000000" required {{ $online ? 'disabled' : '' }}></label>
            </div>
            <dl class="detail-list">
                <div><dt>Dernière connexion</dt><dd>{{ $character->last_login?->format('d/m/Y H:i') ?? '—' }}</dd></div>
                <div><dt>Position</dt><dd>{{ $character->position_region_id }}:{{ $character->position_bank_id }}:{{ $character->position_map_id }} ({{ $character->position_x }}, {{ $character->position_y }})</dd></div>
                <div><dt>Progression</dt><dd>{{ $story['flags'] }} drapeaux · {{ $story['vars'] }} variables</dd></div>
            </dl>
            @if(auth()->user()->canOperateServer())<div class="form-actions compact"><button class="button primary" type="submit" {{ $online ? 'disabled' : '' }}>Enregistrer</button></div>@endif
        </form>
    </section>

    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Dépannage</span><h3>Position et progression</h3></div></div>
        @if(auth()->user()->canOperateServer())
            <form class="inline-form" method="POST" action="{{ route('characters.teleport', $character) }}" data-confirm="Téléporter ce personnage ? Une sauvegarde sera créée.">@csrf
                <label class="field grow"><span>Point sûr</span><select name="destination"><option value="kanto">Départ Kanto</option><option value="hoenn">Départ Hoenn</option></select></label>
                <button class="button secondary" type="submit" {{ $online ? 'disabled' : '' }}>Téléporter</button>
            </form>
            <div class="danger-zone">
                <strong>Recommencer la progression</strong><p>Réinitialise l’histoire, le starter, l’équipe et le sac. Une sauvegarde restaurable est créée automatiquement.</p>
                <form method="POST" action="{{ route('characters.reset', $character) }}" data-confirm="Confirmer la remise à zéro complète de {{ $character->name }} ?" data-confirm-text="RESET">@csrf<button class="button danger" type="submit" {{ $online ? 'disabled' : '' }}>Remettre à zéro</button></form>
            </div>
        @else<p class="muted-copy">Un administrateur peut débloquer ou réinitialiser ce personnage.</p>@endif
    </section>
</div>

<section class="panel timeline-panel">
    <div class="panel-head"><div><span class="eyebrow">Collection</span><h3>Équipe et PC · {{ $pokemon->count() }} Pokémon</h3></div></div>
    <div class="pokemon-grid">
        @forelse($pokemon as $mon)
            <form class="pokemon-card" method="POST" action="{{ route('characters.pokemon.update', [$character, $mon]) }}">@csrf @method('PUT')
                <div class="pokemon-title"><span>#{{ $mon->dex_id }}</span><strong>{{ $mon->nickname ?: 'Pokémon '.$mon->dex_id }}</strong>@if($mon->is_shiny)<i>★ Shiny</i>@endif</div>
                <div class="mini-form-grid">
                    <label>Nom<input name="nickname" value="{{ $mon->nickname }}" maxlength="32" {{ $online ? 'disabled' : '' }}></label>
                    <label>Niveau<input type="number" name="pokemon_level" value="{{ $mon->pokemon_level }}" min="1" max="100" {{ $online ? 'disabled' : '' }}></label>
                    <label>PV<input type="number" name="hp" value="{{ $mon->hp }}" min="0" max="9999" {{ $online ? 'disabled' : '' }}></label>
                    <label>Zone<select name="container" {{ $online ? 'disabled' : '' }}><option value="PARTY" @selected($mon->container === 'PARTY')>Équipe</option><option value="PC" @selected($mon->container === 'PC')>PC</option></select></label>
                    <label>Emplacement<input type="number" name="container_slot" value="{{ $mon->container_slot }}" min="0" max="999" {{ $online ? 'disabled' : '' }}></label>
                    <label class="check-field"><input type="hidden" name="is_shiny" value="0"><input type="checkbox" name="is_shiny" value="1" @checked($mon->is_shiny) {{ $online ? 'disabled' : '' }}> Shiny</label>
                </div>
                @if(auth()->user()->canOperateServer())<button class="button secondary wide" type="submit" {{ $online ? 'disabled' : '' }}>Mettre à jour</button>@endif
            </form>
        @empty<div class="empty-state"><span>◇</span><h3>Aucun Pokémon</h3><p>Le joueur n’a pas encore choisi ou capturé de Pokémon.</p></div>@endforelse
    </div>
</section>

<div class="dashboard-grid dashboard-grid-lower">
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Sac</span><h3>Inventaire</h3></div></div>
        <div class="inventory-list">@forelse($items as $item)<div><code>#{{ $item->item_id }}</code><strong>{{ $item->quantity }}</strong></div>@empty<p class="muted-copy">Le sac est vide.</p>@endforelse</div>
        @if(auth()->user()->canOperateServer())<form class="inline-form" method="POST" action="{{ route('characters.items.update', $character) }}">@csrf @method('PUT')<label class="field"><span>ID objet</span><input type="number" name="item_id" min="1" required {{ $online ? 'disabled' : '' }}></label><label class="field"><span>Quantité</span><input type="number" name="quantity" min="0" max="9999" required {{ $online ? 'disabled' : '' }}></label><button class="button primary" type="submit" {{ $online ? 'disabled' : '' }}>Appliquer</button></form>@endif
    </section>
    <section class="panel">
        <div class="panel-head"><div><span class="eyebrow">Sécurité</span><h3>Sauvegardes du personnage</h3></div></div>
        <div class="activity-list">
            @forelse($snapshots as $snapshot)<div class="activity-row"><span class="activity-mark">↺</span><div class="grow"><strong>{{ $snapshot->reason }}</strong><small>{{ $snapshot->created_at->format('d/m/Y H:i') }}{{ $snapshot->restored_at ? ' · restaurée' : '' }}</small></div>@if(auth()->user()->canOperateServer())<form method="POST" action="{{ route('characters.restore', [$character, $snapshot]) }}" data-confirm="Restaurer cette sauvegarde ? L’état actuel sera lui aussi sauvegardé.">@csrf<button class="text-button" type="submit" {{ $online ? 'disabled' : '' }}>Restaurer</button></form>@endif</div>@empty<div class="empty-state compact"><p>Aucune sauvegarde automatique pour le moment.</p></div>@endforelse
        </div>
    </section>
</div>
@endsection
