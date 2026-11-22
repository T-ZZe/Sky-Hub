# SkyHub

Plugin **Paper 1.21.11** (Java 21) : à chaque connexion, le joueur est téléporté dans un **monde void** sur une **île flottante** avec **3 PNJ** :

| PNJ | Destination par défaut |
|-----|------------------------|
| **Hardcore** | monde normal du serveur (`world`) |
| **Event** | monde `event` |
| **FFA** | monde `ffa` |

Clic droit sur un PNJ = téléportation vers le monde correspondant.

## Installation
1. Compile avec `mvn package` (ou récupère le jar dans l'onglet *Actions* de GitHub).
2. Dépose `target/SkyHub-1.0.0.jar` dans le dossier `plugins/` de ton serveur Paper 1.21.11.
3. Crée/importe tes mondes `event` et `ffa` (ex. avec Multiverse), ou change les noms dans `config.yml`.
4. Lance le serveur : le monde `hub` et l'île sont générés automatiquement.

## Commandes et permissions
| Commande | Description | Permission |
|----------|-------------|------------|
| `/hub` (`/lobby`) | Retour au hub | `skyhub.hub` (tout le monde) |
| `/skyhub reload` | Recharge la config et recrée les PNJ | `skyhub.admin` (op) |

`skyhub.build` (op) : permet de construire dans le hub.

## Configuration
Tout est dans `plugins/SkyHub/config.yml` : nom du monde, taille de l'île, position du spawn, nom/métier/position/destination de chaque PNJ, messages (MiniMessage).

## Notes
- Les inventaires sont partagés entre les mondes par défaut ; utilise un plugin comme Multiverse-Inventories si tu veux les séparer.
- Pour un vrai mode Hardcore, le monde de destination doit lui-même être configuré en hardcore.

## Licence
MIT (ajoute un fichier `LICENSE` si tu veux).
