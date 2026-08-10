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
            $table->timestamp('created_at')->useCurrent();
        });

        Schema::connection('openmmo_game')->dropIfExists('pokemon');
        Schema::connection('openmmo_game')->dropIfExists('characters');
        Schema::connection('openmmo_game')->create('characters', function (Blueprint $table): void {
            $table->bigInteger('id')->primary();
            $table->integer('user_id')->index();
            $table->string('name', 32);
            $table->timestamp('last_login');
            $table->timestamp('created_at');
            $table->integer('money')->default(0);
            $table->smallInteger('position_region_id')->default(0);
        });
        Schema::connection('openmmo_game')->create('pokemon', function (Blueprint $table): void {
            $table->bigInteger('id')->primary();
            $table->bigInteger('owner_id')->index();
        });
    }
}
