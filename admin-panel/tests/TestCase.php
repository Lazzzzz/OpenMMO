<?php

namespace Tests;

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Foundation\Testing\TestCase as BaseTestCase;
use Illuminate\Support\Facades\Schema;

abstract class TestCase extends BaseTestCase
{
    protected function setUp(): void
    {
        parent::setUp();

        Schema::connection('openmmo_login')->dropIfExists('users');
        Schema::connection('openmmo_login')->create('users', function (Blueprint $table): void {
            $table->increments('id');
            $table->string('username', 32)->unique();
            $table->string('display_name', 32);
            $table->string('password_hash', 40);
            $table->integer('token_epoch')->default(0);
            $table->boolean('enabled')->default(true);
            $table->timestamp('banned_until')->nullable();
            $table->string('ban_reason')->nullable();
            $table->timestamp('created_at')->useCurrent();
        });

        Schema::connection('openmmo_game')->dropIfExists('character_skins');
        Schema::connection('openmmo_game')->dropIfExists('character_vars');
        Schema::connection('openmmo_game')->dropIfExists('character_flags');
        Schema::connection('openmmo_game')->dropIfExists('character_items');
        Schema::connection('openmmo_game')->dropIfExists('pokemon');
        Schema::connection('openmmo_game')->dropIfExists('characters');
        Schema::connection('openmmo_game')->create('characters', function (Blueprint $table): void {
            $table->bigInteger('id')->primary();
            $table->integer('user_id')->index();
            $table->string('name', 32);
            $table->timestamp('last_login');
            $table->timestamp('created_at');
            $table->integer('money')->default(0);
            $table->smallInteger('rival_sex')->default(0);
            $table->smallInteger('position_region_id')->default(0);
            $table->smallInteger('position_bank_id')->default(4);
            $table->smallInteger('position_map_id')->default(1);
            $table->smallInteger('position_x')->default(6);
            $table->smallInteger('position_y')->default(6);
            $table->smallInteger('position_facing')->default(0);
            $table->smallInteger('dynamic_warp_region')->nullable();
            $table->smallInteger('dynamic_warp_bank')->nullable();
            $table->smallInteger('dynamic_warp_map')->nullable();
            $table->smallInteger('dynamic_warp_x')->nullable();
            $table->smallInteger('dynamic_warp_y')->nullable();
            $table->smallInteger('dynamic_warp_facing')->nullable();
            $table->timestamp('muted_until')->nullable();
        });
        Schema::connection('openmmo_game')->create('pokemon', function (Blueprint $table): void {
            $table->bigInteger('id')->primary();
            $table->bigInteger('owner_id')->index();
            $table->string('container')->default('PARTY');
            $table->smallInteger('container_slot')->default(0);
            $table->integer('dex_id')->default(1);
            $table->integer('seed')->default(1);
            $table->string('ot')->default('Test');
            $table->string('nickname')->default('');
            $table->smallInteger('pokemon_level')->default(5);
            $table->smallInteger('hp')->default(20);
            $table->integer('xp')->default(0);
            foreach (['ev_hp', 'ev_atk', 'ev_def', 'ev_sp_atk', 'ev_sp_def', 'ev_spd', 'iv_hp', 'iv_atk', 'iv_def', 'iv_sp_atk', 'iv_sp_def', 'iv_spd', 'move1_id', 'move1_pp', 'move2_id', 'move2_pp', 'move3_id', 'move3_pp', 'move4_id', 'move4_pp'] as $column) {
                $table->smallInteger($column)->default(0);
            }
            foreach (['is_shiny', 'has_hidden_ability', 'is_alpha', 'is_secret', 'is_fateful_encounter', 'is_raid_encounter', 'is_egg'] as $column) {
                $table->boolean($column)->default(false);
            }
            $table->timestamp('caught_at')->useCurrent();
        });
        Schema::connection('openmmo_game')->create('character_items', function (Blueprint $table): void {
            $table->bigInteger('character_id');
            $table->integer('item_id');
            $table->integer('quantity');
            $table->primary(['character_id', 'item_id']);
        });
        Schema::connection('openmmo_game')->create('character_flags', function (Blueprint $table): void {
            $table->bigInteger('character_id');
            $table->string('flag_key');
        });
        Schema::connection('openmmo_game')->create('character_vars', function (Blueprint $table): void {
            $table->bigInteger('character_id');
            $table->string('var_key');
            $table->integer('var_value');
        });
        Schema::connection('openmmo_game')->create('character_skins', function (Blueprint $table): void {
            $table->bigInteger('character_id');
            $table->string('slot');
            $table->smallInteger('skin_type')->nullable();
            $table->smallInteger('skin_color')->nullable();
        });
    }
}
