<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class GamePokemon extends Model
{
    protected $connection = 'openmmo_game';

    protected $table = 'pokemon';

    public $timestamps = false;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'is_shiny' => 'boolean',
            'has_hidden_ability' => 'boolean',
            'is_alpha' => 'boolean',
            'caught_at' => 'datetime',
        ];
    }
}
