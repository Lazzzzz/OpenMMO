<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table): void {
            $table->string('role', 20)->default('admin')->after('password');
            $table->boolean('enabled')->default(true)->after('role');
        });

        $firstUserId = DB::table('users')->orderBy('id')->value('id');
        if ($firstUserId !== null) {
            DB::table('users')->where('id', $firstUserId)->update(['role' => 'owner']);
        }
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table): void {
            $table->dropColumn(['role', 'enabled']);
        });
    }
};
