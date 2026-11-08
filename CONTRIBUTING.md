# Contribuir a Rora Studio

## Requisitos

- JDK 21 o superior.
- Git. No hace falta instalar Maven: usa `./mvnw` (Windows PowerShell: `.\mvnw.cmd`).

## Flujo de ramas

Flujo simple, pensado para una sola persona:

| Rama | Propósito |
|---|---|
| `main` | Estable. Siempre compila. Solo recibe merges desde `develop` (pull request, o `git merge --no-ff` tras pasar CI). |
| `develop` | Integración. Aquí se acumulan las fases del roadmap. |
| `fase/N-descripcion` | Ramas cortas por fase (p. ej. `fase/0-red-de-seguridad`). Parten de `develop` y se funden en `develop` con `--no-ff`. |
| `legacy/academic-v0` | Versión académica congelada (tag `v0.1.0-academic`). **No se toca.** |

```bash
git checkout develop
git checkout -b fase/1-nucleo-imagen
# ... commits ...
git checkout develop && git merge --no-ff fase/1-nucleo-imagen
```

## Commits: Conventional Commits

Formato: `tipo(ámbito opcional): descripción en imperativo`.

Tipos: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `ci`, `build`, `perf`, `style`, `revert`.

Ejemplos: `feat: add exposure operation`, `fix(io): flatten alpha before JPG export`, `ci: cache Maven repository`.

Haz commits pequeños y atómicos; el árbol debe compilar después de cada uno.

## Hooks locales (recomendado)

```bash
./tools/install-hooks.sh        # Git Bash / Linux / macOS
.\tools\install-hooks.ps1       # PowerShell
```

- `commit-msg` comprueba el formato Conventional Commits y explica el error si falla.
- `pre-commit` bloquea archivos que no deben versionarse (`.idea/`, `target/`, `.planning/`, claves, modelos `.onnx`) y marcadores de conflicto sin resolver, indicando siempre el motivo.

Si necesitas saltarte un hook de forma excepcional, usa `git commit --no-verify` y explica por qué en el PR.

## Calidad

| Comando | Qué hace | Bloquea |
|---|---|---|
| `./mvnw verify` | Compila, tests, Checkstyle, cobertura JaCoCo | Solo compilación y tests |
| `./mvnw spotless:check` | Comprueba el formato (Google Java Format) | Aún no (modo informe) |
| `./mvnw spotless:apply` | Aplica el formato | — |
| `./mvnw compile spotbugs:check` | Análisis de bugs (usar JDK 21) | Aún no (modo informe) |

## Versionado

[SemVer](https://semver.org/lang/es/). Los cambios se registran en [CHANGELOG.md](CHANGELOG.md) (formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)) bajo `[Unreleased]`.

## Archivos que no se versionan

Notas de planeación (`.planning/`), `CLAUDE.md`, `*.local.md`, modelos de IA (`*.onnx`, `models/`), secretos (`.env`, `*.key`, `*.pem`) y configuración de IDE. Están en `.gitignore`.
