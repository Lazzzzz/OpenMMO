<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class GameCharacter extends Model
{
    protected $connection = 'openmmo_game';

    protected $table = 'characters';

    public $timestamps = false;

    protected $guarded = [];

    protected function casts(): array
    {
        return ['last_login' => 'datetime', 'created_at' => 'datetime'];
    }
}
