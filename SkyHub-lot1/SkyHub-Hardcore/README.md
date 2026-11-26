# SkyHub

Plugin **Paper 1.21.11** (Java 21) : à chaque connexion, le joueur arrive dans un **monde void**, sur une **île flottante enfermée dans une sphère noire**. Trois mobs permettent de rejoindre les différents modes :

| Mob | Nom | Destination par défaut |
|-----|-----|------------------------|
| Wither Squelette (sans épée) | **Hardcore** | monde normal (`world`) |
| Lama | **Event** | monde `event` |
| Breeze | **FFA** | monde `ffa` |

Clic droit sur un mob = téléportation.

## Commandes
| Commande | Description | Permission |
|----------|-------------|------------|
| `/hub`, `/lobby`, `/l` | Retour au hub | tout le monde |
| `/l sp hardcore` / `event` / `ffa` | Pose le mob à ta position (il regarde vers le spawn). **L'ancien est supprimé.** | `skyhub.admin` |
| `/l reload` | Recharge la config et recrée les mobs | `skyhub.admin` |

## Permissions
- `skyhub.build` : casser / poser des blocs dans le hub (**modos uniquement**, op par défaut).
- `skyhub.admin` : `/l sp` et `/l reload`.

Exemple avec LuckPerms : `/lp user <pseudo> permission set skyhub.build true`

## Installation
1. `mvn package` (ou jar depuis l'onglet *Actions* de GitHub).
2. Dépose le jar dans `plugins/`.
3. Crée/importe les mondes `event` et `ffa` (ex. Multiverse), ou change les noms dans `config.yml`.
4. Au premier lancement, le monde `hub`, l'île et la sphère sont générés.

## Configuration
`plugins/SkyHub/config.yml` : monde, île, sphère (rayon, épaisseur, matériau, lumière), type de mob (`entity`), nom, destination et position de chaque mob, messages (MiniMessage).

## Position sauvegardée
Quand un joueur quitte un monde (Hardcore, Event, FFA) pour le hub (`/hub`, `/l`, ou connexion au serveur), sa position est enregistrée dans `plugins/SkyHub/locations.yml`. En revenant par le mob, il est remis au même endroit. Si le joueur meurt, sa position dans ce monde est oubliée. Désactivable avec `lobby.save-positions: false`.

## Notes
- Pour régénérer le hub, arrête le serveur et supprime le dossier du monde `hub`.
- Les inventaires sont partagés entre les mondes (utilise Multiverse-Inventories pour les séparer).
- Pour un vrai mode Hardcore, le monde de destination doit lui-même être en hardcore.
