<?php

use App\Http\Controllers\ActivityController;
use App\Http\Controllers\AdminUserController;
use App\Http\Controllers\Auth\LoginController;
use App\Http\Controllers\CharacterController;
use App\Http\Controllers\DashboardController;
use App\Http\Controllers\PlayerAccessController;
use App\Http\Controllers\PlayerModerationController;
use App\Http\Controllers\ProfileController;
use App\Http\Controllers\ServerController;
use Illuminate\Support\Facades\Route;

Route::middleware('guest')->group(function (): void {
    Route::get('/connexion', [LoginController::class, 'create'])->name('login');
    Route::post('/connexion', [LoginController::class, 'store'])
        ->middleware('throttle:5,1');
});

Route::middleware('auth')->group(function (): void {
    Route::get('/', DashboardController::class)->name('dashboard');
    Route::post('/deconnexion', [LoginController::class, 'destroy'])->name('logout');

    Route::get('/acces', [PlayerAccessController::class, 'index'])->name('accesses.index');
    Route::get('/acces/nouveau', [PlayerAccessController::class, 'create'])->name('accesses.create');
    Route::post('/acces', [PlayerAccessController::class, 'store'])->name('accesses.store');
    Route::get('/acces/{access}', [PlayerAccessController::class, 'show'])->name('accesses.show');
    Route::get('/acces/{access}/modifier', [PlayerAccessController::class, 'edit'])->name('accesses.edit');
    Route::put('/acces/{access}', [PlayerAccessController::class, 'update'])->name('accesses.update');
    Route::patch('/acces/{access}/etat', [PlayerAccessController::class, 'toggle'])->name('accesses.toggle');
    Route::post('/acces/{access}/revoquer', [PlayerAccessController::class, 'revoke'])->name('accesses.revoke');
    Route::post('/acces/{access}/bannir', [PlayerModerationController::class, 'ban'])->name('accesses.ban');
    Route::delete('/acces/{access}/bannir', [PlayerModerationController::class, 'unban'])->name('accesses.unban');
    Route::post('/acces/{access}/mute', [PlayerModerationController::class, 'mute'])->name('accesses.mute');
    Route::delete('/acces/{access}/mute', [PlayerModerationController::class, 'unmute'])->name('accesses.unmute');
    Route::post('/acces/{access}/notes', [PlayerModerationController::class, 'note'])->name('accesses.notes.store');

    Route::get('/personnages/{character}', [CharacterController::class, 'show'])->name('characters.show');
    Route::put('/personnages/{character}', [CharacterController::class, 'update'])->name('characters.update');
    Route::post('/personnages/{character}/teleporter', [CharacterController::class, 'teleport'])->name('characters.teleport');
    Route::post('/personnages/{character}/deconnecter', [CharacterController::class, 'disconnect'])->name('characters.disconnect');
    Route::post('/personnages/{character}/reset', [CharacterController::class, 'resetProgress'])->name('characters.reset');
    Route::post('/personnages/{character}/restaurer/{snapshot}', [CharacterController::class, 'restore'])->name('characters.restore');
    Route::put('/personnages/{character}/pokemon/{pokemon}', [CharacterController::class, 'updatePokemon'])->name('characters.pokemon.update');
    Route::put('/personnages/{character}/inventaire', [CharacterController::class, 'updateItem'])->name('characters.items.update');

    Route::get('/equipe', [AdminUserController::class, 'index'])->name('admins.index');
    Route::post('/equipe', [AdminUserController::class, 'store'])->name('admins.store');
    Route::put('/equipe/{admin}', [AdminUserController::class, 'update'])->name('admins.update');
    Route::patch('/equipe/{admin}/etat', [AdminUserController::class, 'toggle'])->name('admins.toggle');

    Route::get('/profil', [ProfileController::class, 'edit'])->name('profile.edit');
    Route::put('/profil', [ProfileController::class, 'update'])->name('profile.update');
    Route::get('/journal', ActivityController::class)->name('activities.index');

    Route::get('/serveur', [ServerController::class, 'index'])->name('server.index');
    Route::post('/serveur/annonce', [ServerController::class, 'announce'])->name('server.announce');
    Route::post('/serveur/redemarrer', [ServerController::class, 'restart'])->name('server.restart');
    Route::post('/serveur/maintenance', [ServerController::class, 'maintenance'])->name('server.maintenance');
    Route::post('/serveur/sauvegarde', [ServerController::class, 'backup'])->name('server.backup');
    Route::get('/serveur/sauvegardes/{backup}', [ServerController::class, 'download'])->name('server.backups.download');
});
