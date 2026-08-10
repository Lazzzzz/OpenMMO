#!/bin/sh
set -eu

mkdir -p \
    database \
    storage/framework/cache/data \
    storage/framework/sessions \
    storage/framework/views \
    storage/logs \
    bootstrap/cache

if [ "${DB_CONNECTION:-sqlite}" = "sqlite" ]; then
    touch "${DB_DATABASE:-/var/www/html/database/database.sqlite}"
fi

chown -R www-data:www-data database storage bootstrap/cache

php artisan migrate --force --no-interaction
php artisan config:cache
php artisan view:cache

exec "$@"
