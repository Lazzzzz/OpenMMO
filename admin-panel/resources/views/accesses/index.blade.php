@extends('layouts.app')

@section('title', 'Accès joueurs')
@section('heading', 'Accès joueurs')

@section('content')
<div class="page-intro">
    <div><h2>Comptes autorisés</h2><p>Crée, recherche et suspends les connexions au serveur.</p></div>
    <a class="button primary" href="{{ route('accesses.create') }}">+ Créer un accès</a>
</div>

<section class="panel table-panel">
    <form class="searchbar" method="GET" action="{{ route('accesses.index') }}">
        <label><span>⌕</span><input name="q" value="{{ $search }}" placeholder="Rechercher un pseudo ou un identifiant…" aria-label="Rechercher"></label>
        <button class="button secondary" type="submit">Rechercher</button>
        @if ($search)<a class="clear-link" href="{{ route('accesses.index') }}">Effacer</a>@endif
    </form>

    <div class="table-wrap">
        <table>
            <thead><tr><th>Joueur</th><th>Identifiant</th><th>Personnages</th><th>Créé le</th><th>État</th><th></th></tr></thead>
            <tbody>
            @forelse ($accesses as $access)
                <tr>
                    <td><a class="player-cell" href="{{ route('accesses.show', $access) }}"><span class="avatar player">{{ strtoupper(substr($access->display_name, 0, 1)) }}</span><strong>{{ $access->display_name }}</strong></a></td>
                    <td><code>{{ $access->username }}</code></td>
                    <td>{{ $characterCounts[$access->id] ?? 0 }}</td>
                    <td>{{ $access->created_at?->format('d/m/Y') ?? '—' }}</td>
                    <td>
                        @if($access->isBanned())<span class="badge bad">Banni</span>
                        @elseif($access->enabled)<span class="badge good">Actif</span>
                        @else<span class="badge bad">Suspendu</span>@endif
                    </td>
                    <td class="table-actions"><a href="{{ route('accesses.show', $access) }}">Gérer →</a></td>
                </tr>
            @empty
                <tr><td colspan="6"><div class="empty-state"><span>◎</span><h3>Aucun accès trouvé</h3><p>Crée le premier compte joueur ou modifie ta recherche.</p></div></td></tr>
            @endforelse
            </tbody>
        </table>
    </div>
    @if ($accesses->hasPages())<div class="pagination">{{ $accesses->links() }}</div>@endif
</section>
@endsection
