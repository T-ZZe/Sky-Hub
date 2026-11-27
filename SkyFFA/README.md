# SkyFFA

Plugin **Paper 1.21.11** (Java 21), séparé de **SkyHub**. Il contient :
- le **FFA** (5 zones, combat de 20 s, `/spawn`, `/lobby` avec délai),
- les **kits** (`/kit`),
- les **zones protégées** (`/claim`),
- les **PNJ personnalisés** (`/npc`).

SkyHub (hub, Hardcore, One Block, `/menu`) est un autre plugin : installe les deux jars dans `plugins/`. SkyFFA fonctionne seul, mais le retour au hub (`/lobby`) et le mob FFA du hub viennent de SkyHub.

## Installation
1. Jars : onglet *Actions* de GitHub (artefacts `SkyHub` et `SkyFFA`).
2. Dépose `SkyHub.jar` et `SkyFFA.jar` dans `plugins/`.
3. Dans `plugins/SkyFFA/config.yml`, `ffa.world` doit avoir le même nom que `npcs.ffa.world` dans `plugins/SkyHub/config.yml` (`ffa` par défaut).
4. **Reprise automatique** : au premier lancement, SkyFFA **copie** depuis `plugins/SkyHub/` les fichiers `kit-data.yml`, `claims.yml`, `claims/`, `custom-npcs.yml`, ainsi que les spawns des zones (`ffa-spawns`) et le nom du monde FFA. Rien n'est supprimé dans SkyHub (tu pourras les effacer ensuite). Un fichier `.migrated-from-skyhub` évite de recommencer.

## Commandes
| Commande | Description | Permission |
|----------|-------------|------------|
| `/spawn` | Dans le FFA : retour au spawn de ta zone (5 s sans bouger, impossible en combat) | tout le monde |
| `/lobby`, `/hub`, `/l` | Dans le FFA : retour au hub après 5 s sans bouger (impossible en combat). Ailleurs, c'est SkyHub | tout le monde |
| `/ffa spawn <zone>` | Définit le spawn de la zone là où tu es (depuis le monde FFA) | `skyhub.admin` |
| `/ffa reload` | Recharge la config | `skyhub.admin` |
| `/kit`, `/claim`, `/npc` | Voir les sections ci-dessous | voir ci-dessous |

## Permissions
- `skyhub.admin` : (déclarée par SkyHub) commandes admin de SkyFFA.
- `skyhub.spawn` : utiliser `/spawn` (tout le monde par défaut).
- `skyhub.combat.bypass` : ignore le combat FFA et le délai de 5 s (désactivée par défaut, même pour les OP).

## Configuration
`plugins/SkyFFA/config.yml` : `ffa.world`, `ffa.gamemode`, `ffa.item-despawn-seconds`, `ffa.combat-seconds` (20), `ffa.teleport-delay-seconds` (5), `kits.give-on-respawn`, `claims.*` et tous les messages (MiniMessage).

## FFA : 5 zones (UHC / Mace / Crystal / Cart PvP / Pot)
Cliquer sur le mob FFA du hub (plugin SkyHub) ouvre un menu avec les 5 zones. Elles sont sur **la même map** mais chacune a son **spawn** (il n'y a plus de kits) :
- `/ffa spawn <uhc|mace|crystal|cart|pot>` (admin, depuis le monde FFA) : définit le spawn de la zone là où tu es. Sans spawn défini, la zone utilise le spawn du monde.
- À la mort dans le FFA, tu réapparais au spawn de ta zone.

### Combat FFA, `/spawn` et `/lobby`
- **Combat de 20 s** (`ffa.combat-seconds`) dès que tu frappes un joueur ou que tu es frappé (épée, flèche, cristal, TNT, potion néfaste...). Le temps restant s'affiche dans l'action bar.
- **En combat** : `/spawn`, `/lobby` (`/hub`, `/l`), le changement de zone et `/tp` sont refusés. Perles et chorus restent autorisés.
- **Déconnexion en combat = mort** (un joueur ne peut pas être empêché de quitter, il est donc tué et ses objets tombent). Les autres joueurs du FFA sont prévenus.
- **`/spawn`** (FFA uniquement) : retour au spawn de ta zone. **`/lobby`** dans le FFA : retour au hub. Dans les deux cas **5 s sans bouger** (`ffa.teleport-delay-seconds`) : seule la caméra est libre, bouger ou entrer en combat annule. Hors FFA, `/lobby` reste instantané.
- Dans le monde **Hardcore**, `/lobby` est aussi refusé quand tu es en combat.
- Permission `skyhub.combat.bypass` : ignore tout ça (désactivée par défaut, même pour les OP).

### Kits FFA (`/kit`)
Uniquement dans le FFA, une fois une zone choisie (mob FFA). **3 kits par zone** et par joueur : un kit ne marche que dans sa zone.
- `/kit` : ouvre le **Kit Editor** (3 onglets : Équipement / Utilitaire / Nourriture & potions). Clique sur un objet pour le prendre (shift-clic = direct dans l'inventaire).
- `/kit set <1-3>` : sauvegarde ton inventaire comme kit n. `/kit <1-3>` : équipe le kit n.
- Seuls les objets de la zone sont acceptés (nombre max par pile compris). Au respawn, le dernier kit utilisé est rééquipé (`kits.give-on-respawn`).
- **Objets au sol** : dans le monde FFA, un objet resté au sol plus de 180 s disparaît (`ffa.item-despawn-seconds`, 0 = désactivé).
- Quand tu changes de zone ou que tu quittes le FFA, ton stuff est sauvegardé dans l'ancienne zone et retrouvé en y revenant.
- **Armures** : shift-clic sur une armure = choisir le type, les enchantements ou le trim. Toutes les armures ont Curse of Binding.
- **Shulker** : shift-clic sur une shulker dans ton inventaire = l'ouvrir pour la remplir.
- **Restrictions** : pas de netherite en Mace ni UHC ; Crystal sans élytra, mace ni feu d'artifice.
- **Totems** : UHC aucun, Mace / Cart / Pot 1 maximum, Crystal autant que tu veux.
- **Changement de zone** : ton ancien stuff reste 0,5 s dans l'inventaire (tu peux le jeter), puis il est sauvegardé dans l'ancienne zone et celui de la nouvelle zone est récupéré.
- `/kit create <uhc|mace|crystal|cart|pot>` (admin, `skyhub.admin`) : définir les objets de la zone (mets/retire les objets dans les cases, ferme pour sauvegarder). Données : `kit-data.yml`.

## Zones protégées `/claim` (OP uniquement, sélection en créatif)
1. `/claim wand` (créatif + OP) : clic gauche = position 1, clic droit = position 2 (ou `/claim pos1` / `pos2` là où tu es).
2. `/claim create <nom>` crée la zone. Par défaut tout y est **interdit** pour les joueurs.
3. `/claim flag <nom> <place|break|windcharge|pvp|drop> <allow|deny>` :
   - `place` : poser des blocs ; `windcharge` : utiliser des wind charges ; `pvp` : dégâts entre joueurs (autorisé par défaut) ; `drop` : jeter des objets (autorisé par défaut) ;
   - `break` : casser n'importe quel bloc. Si c'est interdit, un joueur peut quand même casser **les blocs qu'il a posés**. Les blocs d'origine résistent aussi aux explosions.
4. `/claim save <nom>` enregistre les blocs de la zone (c'est l'état de référence).
5. `/claim restore <nom> <durée|off>` : la zone est reconstruite toute seule à cet intervalle (ex : `10m`).
   - Pendant une reconstruction (automatique ou `/claim rebuild`), les **joueurs qui sont dans la zone sont téléportés juste au-dessus** et **tous les cristaux d'Ender de la zone disparaissent**.
6. `/claim expire <nom> <durée|off>` : chaque bloc posé par un joueur disparaît après ce délai (ex : `30s`) et le bloc d'origine revient.
7. `/claim pearl <nom> <durée|off>` : délai entre deux lancers de perle d'Ender dans la zone (ex : `5s`), avec le cooldown visible sur l'objet.
7. `/claim rebuild <nom>` reconstruit tout de suite ; `/claim list`, `/claim info [nom]`, `/claim delete <nom>`.

- Un OP en créatif ignore les règles (pour construire l'arène sans que ses blocs disparaissent).
- Seuls les types de blocs sont sauvegardés : le contenu des coffres, panneaux, etc. n'est pas restauré.
- Sauvegarde et reconstruction se font par paquets (`claims.blocks-per-tick`) pour ne pas lagger. Zone max : `claims.max-volume`.
- Données : `plugins/SkyHub/claims.yml` et `plugins/SkyHub/claims/`.

## PNJ `/npc` (OP)
`/npc <mob> <interface|interaction> <nom> [couleur]` crée un PNJ là où tu es (mob au choix : villager, zombie, breeze...). La couleur du nom est un nom (`red`, `dark_red`, `gold`...) ou un code `#rrggbb` ; le `_` du nom devient un espace.
- **interface** : un clic ouvre une interface de **trade**. Ajoute des échanges avec `/npc trade <nom> add` : l'objet vendu dans la main principale, le prix dans la main secondaire. `/npc trade <nom> clear` les efface (12 max).
- **interaction** : un clic donne un objet ou téléporte.
  - `/npc item <nom> [once]` : l'objet tenu en main est donné à chaque clic (`once` = une seule fois par joueur) ;
  - `/npc teleport <nom>` : un clic téléporte à l'endroit où tu te trouves.
- `/npc mod ffa` pose le **Breeze « Changer de PvP »** : un clic ouvre le choix de la zone (UHC, Mace, Crystal, Cart PvP, Pot) sans repasser par le hub.
- `/npc move <nom>`, `/npc remove <nom>`, `/npc list`.
- Les PNJ sont invincibles, immobiles et sauvegardés dans `plugins/SkyHub/custom-npcs.yml`.
