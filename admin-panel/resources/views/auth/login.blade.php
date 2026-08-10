<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Connexion · OpenMMO Control</title>
    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body class="login-page">
<main class="login-shell">
    <section class="login-story">
        <div class="brand light"><span class="brand-mark">O</span><span><strong>OpenMMO</strong><small>Control center</small></span></div>
        <div class="login-copy">
            <span class="live-pill"><i></i> Console privée</span>
            <h1>Ton serveur,<br><em>sous contrôle.</em></h1>
            <p>Gère les accès joueurs, surveille les services et garde une trace de chaque action depuis un seul endroit.</p>
        </div>
        <p class="login-footnote">Réservé aux administrateurs autorisés.</p>
    </section>

    <section class="login-panel">
        <form method="POST" action="{{ url('/connexion') }}" class="login-form">
            @csrf
            <div class="mobile-brand"><span class="brand-mark">O</span><strong>OpenMMO Control</strong></div>
            <p class="eyebrow">Bon retour</p>
            <h2>Connexion administrateur</h2>
            <p class="muted">Utilise ton compte du panneau, pas ton compte joueur.</p>

            @if ($errors->any())
                <div class="flash error" role="alert">{{ $errors->first() }}</div>
            @endif

            <label class="field">
                <span>Adresse e-mail</span>
                <input type="email" name="email" value="{{ old('email') }}" autocomplete="email" required autofocus placeholder="admin@exemple.fr">
            </label>
            <label class="field">
                <span>Mot de passe</span>
                <input type="password" name="password" autocomplete="current-password" required placeholder="••••••••••••">
            </label>
            <label class="checkbox-row">
                <input type="checkbox" name="remember" value="1">
                <span>Rester connecté sur cet appareil</span>
            </label>
            <button class="button primary wide" type="submit">Ouvrir le tableau de bord <span>→</span></button>
        </form>
    </section>
</main>
</body>
</html>
