# OpenMMO

[![](https://dcbadge.limes.pink/api/server/SQwGXyY2gz)](https://discord.gg/SQwGXyY2gz)

## ToC
- [Description](#description)
- [Building](#building)
- [Configuration](#configuration)
- [Launcher](#launcher)
- [Releases](#releases)
- [Documentation](#wiki)
- [License](LICENSE)
- [Disclaimer](#disclaimer)

## Description

> OpenMMO is currently in development and not yet ready for any real use.

OpenMMO is an open-source implementation of the PokeMMO server.
The goal is it to provide a free and open-source alternative to the PokeMMO server.

## Building

The map data is generated at build time from the [pret](https://github.com/pret)
decompilation projects, which are vendored as git submodules under `decomp/`.
Clone the repository with its submodules:

```bash
git clone --recurse-submodules <repo-url>
# or, for an existing clone:
git submodule update --init --recursive
```

Without the submodules the `:codegen` build fails, because the generator has no
decomp data to read.

### ROMs

A dialog id is a file offset into the retail GBA ROM. The decomp is byte-identical
to it, so the generator encodes a text from the decomp, finds those bytes in the
ROM, and packs the offset into the id. The decomp alone has no offsets.

Put the Emerald (`BPEE`, Hoenn) and FireRed (`BPRE`, Kanto) ROMs in `roms/`.
Filenames do not matter, each is identified by the game code in its GBA header.
The folder is **gitignored**, this project ships no ROMs.

Without them the build still succeeds and every dialog id is `0`, so CI passes but
the client shows the wrong text.

## Configuration

All local configuration and secrets live in a `.env` file at the repository
root. It is **gitignored**, never commit it. Use the tracked
[`.env.example`](.env.example) as the template:

```bash
cp .env.example .env          # then edit the values
docker compose up -d          # start all docker containers
./gradlew :server.login:run   # binds 0.0.0.0:2106, blocks
./gradlew :server.game:run    # binds 0.0.0.0:7777, blocks
```

For local-only tweaks to the container setup, create a
`docker-compose.override.yml` (also gitignored). 
Docker Compose merges it automatically on `docker compose up`. 
For deployment,supply a proper `.env` and run `docker compose -f docker-compose.yml up -d` to skip any override.

### Server key

Both servers share one private key. A local build generates it, so development
needs no setup.

Released archives ship no keys. Generate a pair with
`./gradlew :keys:generateGame` and pass the private key to both servers through
`OPENMMO_GAME_PRIVATE_KEY` (the PEM) or `OPENMMO_GAME_PRIVATE_KEY_FILE` (a path
to it). Clients need a patched build carrying the matching public key.

## Launcher

The launcher downloads a PokeMMO client from the official servers, applies OpenMMO's patches to
it and starts it. It keeps its own directory and never touches a retail PokeMMO install.

```bash
./gradlew :launcher:dev       # serves a feed on loopback, then opens the launcher
```

That feed points the client at `127.0.0.1:2106`, so run the servers alongside it.
Leave the command running while you play, because the client reads the feed for as
long as it is open.

A distribution bakes in the published feed instead of the loopback one:

```bash
./gradlew :launcher:createDistributable -Popenmmo.feedOrigin=https://feed.openmmo.dev
```

See [Running the game client](docs/src/content/docs/guides/running-the-client.md)
for the details.

## Releases

[release-please](https://github.com/googleapis/release-please) cuts releases from
the commit history. Pull requests are squash merged, so their titles become the
commit messages it reads and must follow
[Conventional Commits](https://www.conventionalcommits.org/). CI rejects titles
that do not.

`feat` bumps the minor version, `fix` the patch version. Below `1.0.0` a
breaking change bumps the minor version instead of jumping to `1.0.0`.

Every push to `master` opens or updates a release pull request. Merging it tags
the release and attaches the server archives. The version lives in
`gradle.properties` and applies to every module, do not edit it by hand.

## Wiki

The documentation wiki can be found [here](https://openmmo.readthedocs.io/en/latest/).
Or you can navigate to it via the `docs` folder in this repository.

## Disclaimer
[PokeMMO](https://pokemmo.eu/) is not affiliated with this project in any way.
Hosting/Using a private server might be against the [PokeMMO ToS](https://pokemmo.com/tos/).
