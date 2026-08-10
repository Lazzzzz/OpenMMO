<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;

class PlayerAccess extends Model
{
    protected $connection = 'openmmo_login';

    protected $table = 'users';

    public $timestamps = false;

    protected $fillable = ['username', 'display_name', 'password_hash', 'enabled', 'token_epoch'];

    protected $hidden = ['password_hash'];

    protected function casts(): array
    {
        return ['enabled' => 'boolean', 'created_at' => 'datetime'];
    }

    public function scopeSearch(Builder $query, ?string $search): Builder
    {
        if (blank($search)) {
            return $query;
        }

        $term = '%'.strtolower(trim($search)).'%';

        return $query->where(function (Builder $query) use ($term): void {
            $query->whereRaw('LOWER(username) LIKE ?', [$term])
                ->orWhereRaw('LOWER(display_name) LIKE ?', [$term]);
        });
    }

    public static function passwordHash(string $password): string
    {
        return sha1($password);
    }
}
