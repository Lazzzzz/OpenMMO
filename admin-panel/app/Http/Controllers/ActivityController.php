<?php

namespace App\Http\Controllers;

use App\Models\AdminActivity;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\View\View;

class ActivityController extends Controller
{
    public function __invoke(Request $request): View
    {
        $action = $request->string('action')->trim()->toString();
        $adminId = $request->integer('admin');
        $activities = AdminActivity::query()
            ->when($action, fn ($query) => $query->where('action', 'like', $action.'%'))
            ->when($adminId, fn ($query) => $query->where('user_id', $adminId))
            ->latest()
            ->paginate(30)
            ->withQueryString();

        return view('activities.index', [
            'activities' => $activities,
            'admins' => User::query()->orderBy('name')->get(),
            'action' => $action,
            'adminId' => $adminId,
        ]);
    }
}
