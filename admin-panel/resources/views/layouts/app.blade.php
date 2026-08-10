<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>@yield('title', 'Tableau de bord') · OpenMMO Control</title>
    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body>
<div class="app-shell">
    <aside class="sidebar" id="sidebar">
        <a class="brand" href="{{ route('dashboard') }}">
            <span class="brand-mark">O</span>
            <span><strong>OpenMMO</strong><small>Control center</small></span>
        </a>

        <nav class="nav" aria-label="Navigation principale">
            <span class="nav-label">Pilotage</span>
            <a href="{{ route('dashboard') }}" class="nav-link {{ request()->routeIs('dashboard') ? 'active' : '' }}">
                <span class="nav-icon">⌁</span> Vue d’ensemble
            </a>
            <a href="{{ route('accesses.index') }}" class="nav-link {{ request()->routeIs('accesses.*') ? 'active' : '' }}">
                <span class="nav-icon">◎</span> Accès joueurs
            </a>
        </nav>

        <div class="sidebar-footer">
            <div class="admin-chip">
                <span class="avatar">{{ strtoupper(substr(auth()->user()->name, 0, 1)) }}</span>
                <span><strong>{{ auth()->user()->name }}</strong><small>Administrateur</small></span>
            </div>
            <form method="POST" action="{{ route('logout') }}">
                @csrf
                <button class="logout-button" type="submit">Se déconnecter</button>
            </form>
        </div>
    </aside>

    <main class="main">
        <header class="topbar">
            <button class="menu-button" type="button" data-menu-toggle aria-label="Ouvrir le menu">☰</button>
            <div>
                <p class="eyebrow">Administration privée</p>
                <h1>@yield('heading', 'OpenMMO')</h1>
            </div>
            <a class="button primary top-action" href="{{ route('accesses.create') }}">+ Nouvel accès</a>
        </header>

        <div class="content">
            @if (session('success'))
                <div class="flash success" role="status">✓ {{ session('success') }}</div>
            @endif
            @if ($errors->any())
                <div class="flash error" role="alert">
                    <strong>Vérifie les informations :</strong>
                    <ul>@foreach ($errors->all() as $error)<li>{{ $error }}</li>@endforeach</ul>
                </div>
            @endif

            @yield('content')
        </div>
    </main>
</div>
</body>
</html>
