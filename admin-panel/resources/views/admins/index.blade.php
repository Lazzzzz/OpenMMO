@extends('layouts.app')

@section('title', 'Équipe')
@section('heading', 'Équipe administrative')

@section('content')
<div class="page-intro"><div><h2>Accès au panneau</h2><p>Ajoute des membres et limite leurs droits selon leur rôle.</p></div></div>

<section class="panel">
    <div class="panel-head"><div><span class="eyebrow">Invitation</span><h3>Créer un administrateur</h3></div></div>
    <form method="POST" action="{{ route('admins.store') }}">@csrf
        <div class="form-grid admin-form-grid">
            <label class="field"><span>Nom</span><input name="name" maxlength="80" required placeholder="Julien"></label>
            <label class="field"><span>Adresse e-mail</span><input type="email" name="email" required placeholder="julien@exemple.fr"></label>
            <label class="field"><span>Rôle</span><select name="role"><option value="moderator">Modérateur</option><option value="admin">Administrateur</option><option value="owner">Propriétaire</option></select></label>
            <label class="field"><span>Mot de passe temporaire</span><input type="password" name="password" minlength="12" required autocomplete="new-password"></label>
            <label class="field"><span>Confirmation</span><input type="password" name="password_confirmation" minlength="12" required autocomplete="new-password"></label>
        </div>
        <div class="role-help"><span><strong>Modérateur</strong>Joueurs, bannissements, mutes et notes.</span><span><strong>Administrateur</strong>Ajoute les modifications personnages et la console serveur.</span><span><strong>Propriétaire</strong>Gère aussi les membres de l’équipe.</span></div>
        <div class="form-actions"><button class="button primary" type="submit">Créer le compte</button></div>
    </form>
</section>

<section class="panel table-panel timeline-panel">
    <div class="panel-head panel-head-padded"><div><span class="eyebrow">Membres</span><h3>{{ $admins->count() }} compte{{ $admins->count() > 1 ? 's' : '' }}</h3></div></div>
    <div class="admin-list">
        @foreach($admins as $admin)
            <details class="admin-row" @if($admin->is(auth()->user())) open @endif>
                <summary><span class="avatar">{{ strtoupper(substr($admin->name, 0, 1)) }}</span><span class="grow"><strong>{{ $admin->name }}</strong><small>{{ $admin->email }}</small></span><span class="badge {{ $admin->enabled ? 'good' : 'bad' }}">{{ $admin->enabled ? $admin->roleLabel() : 'Désactivé' }}</span><span>⌄</span></summary>
                <form method="POST" action="{{ route('admins.update', $admin) }}">@csrf @method('PUT')
                    <div class="form-grid admin-form-grid">
                        <label class="field"><span>Nom</span><input name="name" value="{{ $admin->name }}" required></label>
                        <label class="field"><span>E-mail</span><input type="email" name="email" value="{{ $admin->email }}" required></label>
                        <label class="field"><span>Rôle</span><select name="role"><option value="moderator" @selected($admin->role === 'moderator')>Modérateur</option><option value="admin" @selected($admin->role === 'admin')>Administrateur</option><option value="owner" @selected($admin->role === 'owner')>Propriétaire</option></select></label>
                        <label class="field"><span>Nouveau mot de passe</span><input type="password" name="password" minlength="12" autocomplete="new-password" placeholder="Laisser vide pour conserver"></label>
                        <label class="field"><span>Confirmation</span><input type="password" name="password_confirmation" minlength="12" autocomplete="new-password"></label>
                    </div>
                    <div class="form-actions compact"><button class="button primary" type="submit">Enregistrer</button></div>
                </form>
                @unless($admin->is(auth()->user()))<form method="POST" action="{{ route('admins.toggle', $admin) }}" data-confirm="{{ $admin->enabled ? 'Désactiver cet administrateur ?' : 'Réactiver cet administrateur ?' }}">@csrf @method('PATCH')<button class="button {{ $admin->enabled ? 'danger' : 'success' }}" type="submit">{{ $admin->enabled ? 'Désactiver' : 'Réactiver' }}</button></form>@endunless
            </details>
        @endforeach
    </div>
</section>
@endsection
