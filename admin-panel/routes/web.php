<?php

use App\Http\Controllers\Auth\LoginController;
use App\Http\Controllers\DashboardController;
use App\Http\Controllers\PlayerAccessController;
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
});
