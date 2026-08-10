@extends('layouts.app')

@section('title', 'Modifier '.$access->display_name)
@section('heading', 'Modifier un accès')

@section('content')
<div class="page-intro"><div><a class="back-link" href="{{ route('accesses.show', $access) }}">← Retour au compte</a><h2>{{ $access->display_name }}</h2><p>Modifie l’identité ou remplace le mot de passe de connexion.</p></div></div>
<section class="panel form-panel">
    <form method="POST" action="{{ route('accesses.update', $access) }}">
        @csrf @method('PUT')
        @include('accesses._form', ['access' => $access])
    </form>
</section>
@endsection
