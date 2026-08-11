<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class CharacterSnapshot extends Model
{
    protected $fillable = ['character_id', 'user_id', 'reason', 'payload', 'restored_at'];

    protected function casts(): array
    {
        return ['payload' => 'array', 'restored_at' => 'datetime'];
    }
}
