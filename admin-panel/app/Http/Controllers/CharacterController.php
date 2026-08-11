<?php

namespace App\Http\Controllers;

use App\Models\CharacterSnapshot;
use App\Models\GameCharacter;
use App\Models\GamePokemon;
use App\Models\PlayerAccess;
use App\Services\AdminActivityRecorder;
use App\Services\CharacterSnapshotService;
use App\Services\GameAdminClient;
use Illuminate\Http\Client\RequestException;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;
use Throwable;

class CharacterController extends Controller
{
    public function __construct(
        private readonly GameAdminClient $game,
        private readonly CharacterSnapshotService $snapshots,
        private readonly AdminActivityRecorder $activities,
    ) {}

    public function show(GameCharacter $character): View
    {
        $access = PlayerAccess::query()->findOrFail($character->user_id);
        $pokemon = GamePokemon::query()->where('owner_id', $character->id)->orderBy('container')->orderBy('container_slot')->get();
        $items = DB::connection('openmmo_game')->table('character_items')->where('character_id', $character->id)->orderBy('item_id')->get();
        $story = [
            'flags' => DB::connection('openmmo_game')->table('character_flags')->where('character_id', $character->id)->count(),
            'vars' => DB::connection('openmmo_game')->table('character_vars')->where('character_id', $character->id)->count(),
        ];
        $onlineIds = collect($this->game->onlinePlayers())->pluck('id')->map(fn ($id) => (int) $id);

        return view('characters.show', [
            'character' => $character,
            'access' => $access,
            'pokemon' => $pokemon,
            'items' => $items,
            'story' => $story,
            'online' => $onlineIds->contains((int) $character->id),
            'snapshots' => CharacterSnapshot::query()->where('character_id', $character->id)->latest()->limit(10)->get(),
        ]);
    }

    public function update(Request $request, GameCharacter $character): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        $data = $request->validate([
            'name' => ['required', 'string', 'min:2', 'max:32', 'regex:/^[\pL\pN _-]+$/u'],
            'money' => ['required', 'integer', 'min:0', 'max:2000000000'],
        ]);
        $before = $character->only(['name', 'money']);
        $character->update($data);
        $this->activities->record($request, 'character.updated', "Personnage {$character->name} modifié", 'character', $character->id, ['before' => $before, 'after' => $data]);

        return back()->with('success', 'Personnage mis à jour.');
    }

    public function teleport(Request $request, GameCharacter $character): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        $data = $request->validate(['destination' => ['required', Rule::in(['kanto', 'hoenn'])]]);
        $this->snapshots->capture($character, 'Avant téléportation', $request->user()->id);
        $position = $data['destination'] === 'hoenn'
            ? ['position_region_id' => 1, 'position_bank_id' => 50, 'position_map_id' => 9, 'position_x' => 3, 'position_y' => 10, 'position_facing' => 3]
            : ['position_region_id' => 0, 'position_bank_id' => 4, 'position_map_id' => 1, 'position_x' => 6, 'position_y' => 6, 'position_facing' => 0];
        $position += [
            'dynamic_warp_region' => null, 'dynamic_warp_bank' => null, 'dynamic_warp_map' => null,
            'dynamic_warp_x' => null, 'dynamic_warp_y' => null, 'dynamic_warp_facing' => null,
        ];
        DB::connection('openmmo_game')->table('characters')->where('id', $character->id)->update($position);
        $this->activities->record($request, 'character.teleported', "{$character->name} téléporté vers ".strtoupper($data['destination']), 'character', $character->id);

        return back()->with('success', 'Téléportation enregistrée.');
    }

    public function resetProgress(Request $request, GameCharacter $character): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        $this->snapshots->capture($character, 'Avant remise à zéro', $request->user()->id);
        $this->game->resetCharacter((int) $character->id);
        $this->activities->record($request, 'character.reset', "Progression de {$character->name} remise à zéro", 'character', $character->id);

        return back()->with('success', 'Progression remise à zéro. Une sauvegarde restaurable a été créée.');
    }

    public function restore(Request $request, GameCharacter $character, CharacterSnapshot $snapshot): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        abort_unless((int) $snapshot->character_id === (int) $character->id, 404);
        $this->snapshots->capture($character, 'Avant restauration', $request->user()->id);
        $this->snapshots->restore($snapshot);
        $this->activities->record($request, 'character.restored', "Sauvegarde #{$snapshot->id} restaurée pour {$character->name}", 'character', $character->id);

        return back()->with('success', 'Sauvegarde restaurée.');
    }

    public function updatePokemon(Request $request, GameCharacter $character, GamePokemon $pokemon): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        abort_unless((int) $pokemon->owner_id === (int) $character->id, 404);
        $data = $request->validate([
            'nickname' => ['nullable', 'string', 'max:32'],
            'pokemon_level' => ['required', 'integer', 'min:1', 'max:100'],
            'hp' => ['required', 'integer', 'min:0', 'max:9999'],
            'container' => ['required', Rule::in(['PARTY', 'PC'])],
            'container_slot' => ['required', 'integer', 'min:0', 'max:999'],
            'is_shiny' => ['nullable', 'boolean'],
        ]);
        $conflict = GamePokemon::query()
            ->where('owner_id', $character->id)
            ->where('container', $data['container'])
            ->where('container_slot', $data['container_slot'])
            ->whereKeyNot($pokemon->id)
            ->exists();
        if ($conflict) {
            throw ValidationException::withMessages(['container_slot' => 'Cet emplacement est déjà occupé.']);
        }
        $data['nickname'] ??= '';
        $data['is_shiny'] = $request->boolean('is_shiny');
        $pokemon->update($data);
        $this->activities->record($request, 'pokemon.updated', "Pokémon #{$pokemon->dex_id} modifié pour {$character->name}", 'character', $character->id);

        return back()->with('success', 'Pokémon mis à jour.');
    }

    public function givePokemon(Request $request, GameCharacter $character): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        $data = $request->validate([
            'dex_id' => ['required', 'integer', 'min:1', 'max:386'],
            'pokemon_level' => ['required', 'integer', 'min:1', 'max:100'],
            'container' => ['required', Rule::in(['PARTY', 'PC'])],
            'nickname' => ['nullable', 'string', 'max:32'],
            'is_shiny' => ['nullable', 'boolean'],
        ]);
        $this->snapshots->capture($character, 'Avant ajout d’un Pokémon', $request->user()->id);
        try {
            $this->game->givePokemon(
                (int) $character->id,
                (int) $data['dex_id'],
                (int) $data['pokemon_level'],
                $data['container'],
                $data['nickname'] ?? '',
                $request->boolean('is_shiny'),
            );
        } catch (RequestException $error) {
            $message = match ($error->response->status()) {
                409 => 'Le personnage est connecté ou la destination choisie est pleine.',
                422 => 'Ce numéro de Pokédex n’est pas disponible dans cette version du jeu.',
                default => 'Le serveur n’a pas pu ajouter ce Pokémon. Réessaie dans un instant.',
            };
            throw ValidationException::withMessages(['pokemon' => $message]);
        }
        $this->activities->record($request, 'pokemon.given', "Pokémon #{$data['dex_id']} donné à {$character->name}", 'character', $character->id, [
            'level' => (int) $data['pokemon_level'],
            'container' => $data['container'],
            'shiny' => $request->boolean('is_shiny'),
        ]);

        return back()->with('success', 'Pokémon ajouté. Une sauvegarde restaurable a été créée.');
    }

    public function deletePokemon(Request $request, GameCharacter $character, GamePokemon $pokemon): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        abort_unless((int) $pokemon->owner_id === (int) $character->id, 404);
        $this->snapshots->capture($character, 'Avant suppression d’un Pokémon', $request->user()->id);
        $label = $pokemon->nickname ?: 'Pokémon '.$pokemon->dex_id;
        try {
            $this->game->deletePokemon((int) $character->id, (int) $pokemon->id);
        } catch (RequestException $error) {
            $message = $error->response->status() === 409
                ? 'Le personnage s’est reconnecté. Déconnecte-le avant de supprimer ce Pokémon.'
                : 'Le serveur n’a pas pu supprimer ce Pokémon. Réessaie dans un instant.';
            throw ValidationException::withMessages(['pokemon' => $message]);
        }
        $this->activities->record($request, 'pokemon.deleted', "{$label} supprimé de {$character->name}", 'character', $character->id, [
            'pokemon_id' => (int) $pokemon->id,
            'dex_id' => (int) $pokemon->dex_id,
        ]);

        return back()->with('success', 'Pokémon supprimé. Tu peux le récupérer depuis la sauvegarde créée automatiquement.');
    }

    public function updateItem(Request $request, GameCharacter $character): RedirectResponse
    {
        $this->authorizeOperator($request);
        $this->ensureOffline($character);
        $data = $request->validate([
            'item_id' => ['required', 'integer', 'min:1', 'max:100000'],
            'quantity' => ['required', 'integer', 'min:0', 'max:9999'],
        ]);
        $query = DB::connection('openmmo_game')->table('character_items')
            ->where('character_id', $character->id)->where('item_id', $data['item_id']);
        if ((int) $data['quantity'] === 0) {
            $query->delete();
        } else {
            DB::connection('openmmo_game')->table('character_items')->updateOrInsert(
                ['character_id' => $character->id, 'item_id' => $data['item_id']],
                ['quantity' => $data['quantity']],
            );
        }
        $this->activities->record($request, 'inventory.updated', "Objet #{$data['item_id']} ajusté pour {$character->name}", 'character', $character->id, ['quantity' => $data['quantity']]);

        return back()->with('success', 'Inventaire mis à jour.');
    }

    public function disconnect(Request $request, GameCharacter $character): RedirectResponse
    {
        $this->authorizeOperator($request);
        $disconnected = $this->game->disconnect((int) $character->id);
        $this->activities->record($request, 'character.disconnected', "Déconnexion forcée de {$character->name}", 'character', $character->id);

        return back()->with('success', $disconnected ? 'Joueur déconnecté.' : 'Le joueur était déjà hors ligne.');
    }

    private function ensureOffline(GameCharacter $character): void
    {
        try {
            $online = collect($this->game->requireOnlinePlayers())->contains(fn (array $player) => (int) ($player['id'] ?? 0) === (int) $character->id);
        } catch (Throwable) {
            throw ValidationException::withMessages(['character' => 'Impossible de vérifier si ce personnage est connecté. Réessaie quand le serveur de jeu répond.']);
        }

        if ($online) {
            throw ValidationException::withMessages(['character' => 'Déconnecte ce personnage avant de modifier ses données.']);
        }
    }

    private function authorizeOperator(Request $request): void
    {
        abort_unless($request->user()?->canOperateServer(), 403);
    }
}
