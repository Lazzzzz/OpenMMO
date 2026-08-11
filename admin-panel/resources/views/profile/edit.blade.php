@extends('layouts.app')

@section('title', 'Mon profil')
@section('heading', 'Mon profil')

@section('content')
<div class="page-intro"><div><h2>{{ $admin->name }}</h2><p>{{ $admin->email }} · {{ $admin->roleLabel() }}</p></div></div>
<section class="panel form-panel">
    <div class="panel-head"><div><span class="eyebrow">Sécurité</span><h3>Nom et mot de passe</h3></div></div>
    <form method="POST" action="{{ route('profile.update') }}">@csrf @method('PUT')
        <div class="form-grid">
            <label class="field"><span>Nom affiché</span><input name="name" value="{{ old('name', $admin->name) }}" required></label>
            <label class="field"><span>Mot de passe actuel</span><input type="password" name="current_password" required autocomplete="current-password"></label>
            <label class="field"><span>Nouveau mot de passe</span><input type="password" name="password" minlength="12" autocomplete="new-password"><small>Laisse vide pour conserver l’actuel.</small></label>
            <label class="field"><span>Confirmation</span><input type="password" name="password_confirmation" minlength="12" autocomplete="new-password"></label>
        </div>
        <div class="form-actions"><button class="button primary" type="submit">Mettre à jour mon profil</button></div>
    </form>
</section>
@endsection
