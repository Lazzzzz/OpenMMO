# OpenMMO Control

Panneau d'administration Laravel pour le serveur OpenMMO.

## Fonctions

- connexion administrateur séparée des comptes joueurs ;
- état des serveurs de connexion et de jeu ;
- statistiques des comptes, personnages et Pokémon ;
- recherche et consultation des accès joueurs ;
- création d'un accès compatible avec le protocole de connexion OpenMMO ;
- changement de mot de passe et révocation des sessions mémorisées ;
- suspension/réactivation sans supprimer les personnages ;
- journal local des actions administratives ;
- fiches personnages avec équipe, PC, inventaire, argent et position ;
- téléportation de dépannage et remise à zéro restaurable de la progression ;
- bannissements temporaires, mutes et notes internes ;
- équipe d'administration avec rôles propriétaire, administrateur et modérateur ;
- joueurs en ligne, annonces globales, logs et redémarrage des services ;
- mode maintenance et sauvegardes PostgreSQL téléchargeables.

## Installation locale

Depuis la racine du dépôt, démarrez les bases OpenMMO puis préparez le panneau :

```bash
docker compose up -d
cd admin-panel
cp .env.example .env
composer install
npm install
php artisan key:generate
php artisan migrate
npm run build
php artisan admin:create
php artisan serve --host=127.0.0.1 --port=8080
```

Le panneau est alors disponible sur `http://127.0.0.1:8080`.

Le serveur de connexion doit avoir exécuté sa migration Flyway `V3__add_user_enabled.sql`
avant d'utiliser la suspension des comptes. Un démarrage normal du serveur applique cette
migration automatiquement.

## Configuration

Le panneau conserve ses administrateurs et son journal dans sa base Laravel (`DB_*`). Les
variables `OPENMMO_LOGIN_DB_*` et `OPENMMO_GAME_DB_*` configurent les deux connexions aux
bases du jeu. Les variables `OPENMMO_LOGIN_HOST`, `OPENMMO_LOGIN_PORT`,
`OPENMMO_GAME_HOST` et `OPENMMO_GAME_PORT` servent au contrôle de disponibilité.
`OPENMMO_ADMIN_URL` et `OPENMMO_ADMIN_TOKEN` protègent l'interface privée du serveur de jeu.
`OPENMMO_OPS_URL` et `OPENMMO_OPS_TOKEN` protègent l'agent d'exploitation interne.

En production : utilisez HTTPS, placez le site derrière Nginx ou Caddy, mettez
`APP_ENV=production` et `APP_DEBUG=false`, employez des mots de passe de base distincts et
restreignez l'accès PostgreSQL au serveur qui héberge le panneau.

## Image de production

Le `Dockerfile` produit une image Apache/PHP autonome. Les dépendances Composer et les assets
Vite sont construits dans des étapes séparées et aucun fichier `.env` n'entre dans l'image.
Le conteneur applique les migrations Laravel au démarrage puis met en cache la configuration
et les vues. Montez un volume persistant sur `/var/www/html/database` lorsque la base du
panneau utilise SQLite.

## Vérification

```bash
composer test
vendor/bin/pint --test
npm run build
```
