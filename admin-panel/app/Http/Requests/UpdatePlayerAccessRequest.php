<?php

namespace App\Http\Requests;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class UpdatePlayerAccessRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'username' => [
                'required', 'string', 'min:3', 'max:32', 'regex:/^[A-Za-z0-9_]+$/',
                Rule::unique('openmmo_login.users', 'username')->ignore($this->route('access')),
            ],
            'display_name' => ['required', 'string', 'min:2', 'max:32'],
            'password' => ['nullable', 'string', 'min:8', 'max:72', 'confirmed'],
        ];
    }
}
