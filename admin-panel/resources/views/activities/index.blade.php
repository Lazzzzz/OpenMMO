@extends('layouts.app')

@section('title', 'Journal')
@section('heading', 'Journal d’activité')

@section('content')
<div class="page-intro"><div><h2>Traçabilité administrative</h2><p>Chaque modification sensible est conservée avec son auteur et son adresse IP.</p></div></div>
<section class="panel table-panel">
    <form class="searchbar" method="GET" action="{{ route('activities.index') }}">
        <label><select name="action"><option value="">Toutes les actions</option>@foreach(['access' => 'Accès joueurs', 'moderation' => 'Modération', 'character' => 'Personnages', 'pokemon' => 'Pokémon', 'inventory' => 'Inventaire', 'server' => 'Serveur', 'admin' => 'Équipe'] as $value => $label)<option value="{{ $value }}" @selected($action === $value)>{{ $label }}</option>@endforeach</select></label>
        <label><select name="admin"><option value="0">Tous les administrateurs</option>@foreach($admins as $admin)<option value="{{ $admin->id }}" @selected($adminId === $admin->id)>{{ $admin->name }}</option>@endforeach</select></label>
        <button class="button secondary" type="submit">Filtrer</button>
    </form>
    <div class="activity-ledger">
        @forelse($activities as $activity)
            <article><span class="activity-mark">✓</span><div class="grow"><strong>{{ $activity->description }}</strong><small>{{ $activity->action }} · {{ $activity->created_at->format('d/m/Y H:i:s') }} · {{ $activity->ip_address ?? 'IP inconnue' }}</small></div><span class="badge subtle">{{ $activity->user_id ? ($admins->firstWhere('id', $activity->user_id)?->name ?? 'Supprimé') : 'Système' }}</span></article>
        @empty<div class="empty-state"><span>≡</span><h3>Aucune activité</h3><p>Modifie les filtres ou effectue une action dans le panneau.</p></div>@endforelse
    </div>
    @if($activities->hasPages())<div class="pagination">{{ $activities->links() }}</div>@endif
</section>
@endsection
