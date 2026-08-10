<div class="form-grid">
    <label class="field">
        <span>Identifiant de connexion</span>
        <input name="username" value="{{ old('username', $access->username ?? '') }}" maxlength="32" required placeholder="ex. julien2001">
        <small>Lettres, chiffres et underscore uniquement.</small>
    </label>
    <label class="field">
        <span>Nom affiché</span>
        <input name="display_name" value="{{ old('display_name', $access->display_name ?? '') }}" maxlength="32" required placeholder="ex. Julien">
        <small>Le nom visible dans le panneau et les listes.</small>
    </label>
    <label class="field">
        <span>{{ isset($access) ? 'Nouveau mot de passe' : 'Mot de passe' }}</span>
        <input type="password" name="password" {{ isset($access) ? '' : 'required' }} autocomplete="new-password" placeholder="8 caractères minimum">
        @isset($access)<small>Laisse vide pour conserver le mot de passe actuel.</small>@endisset
    </label>
    <label class="field">
        <span>Confirmer le mot de passe</span>
        <input type="password" name="password_confirmation" {{ isset($access) ? '' : 'required' }} autocomplete="new-password" placeholder="Retape le mot de passe">
    </label>
</div>

@unless(isset($access))
<label class="switch-row">
    <input type="hidden" name="enabled" value="0">
    <input type="checkbox" name="enabled" value="1" {{ old('enabled', true) ? 'checked' : '' }}>
    <span class="switch"></span>
    <span><strong>Activer immédiatement</strong><small>Le joueur pourra se connecter dès la création.</small></span>
</label>
@endunless

<div class="form-actions">
    <a class="button secondary" href="{{ isset($access) ? route('accesses.show', $access) : route('accesses.index') }}">Annuler</a>
    <button class="button primary" type="submit">{{ isset($access) ? 'Enregistrer les modifications' : 'Créer l’accès' }}</button>
</div>
