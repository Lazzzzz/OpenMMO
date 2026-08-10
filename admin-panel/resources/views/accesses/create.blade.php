@extends('layouts.app')

@section('title', 'Nouvel accès')
@section('heading', 'Nouvel accès')

@section('content')
<div class="page-intro"><div><a class="back-link" href="{{ route('accesses.index') }}">← Retour aux accès</a><h2>Inviter un joueur</h2><p>Ces identifiants permettront de se connecter directement au client OpenMMO.</p></div></div>
<section class="panel form-panel">
    <form method="POST" action="{{ route('accesses.store') }}">
        @csrf
        @include('accesses._form')
    </form>
</section>
@endsection
