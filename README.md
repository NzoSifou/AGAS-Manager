# AGAS Manager — Android Games Ads Skipper

AGAS passe automatiquement les publicités plein écran des jeux mobiles : il attend que le bouton
de fermeture apparaisse et appuie dessus à ta place, y compris pour les pubs en plusieurs étapes
(croix, puis « Skip », puis écran de fin…).

AGAS **ne bloque aucune publicité** : la pub s'affiche normalement, elle est comptabilisée et le
développeur du jeu est rémunéré. Les récompenses des pubs « récompensées » sont conservées.

- Aucune IA, aucun serveur : tout se passe sur le téléphone, rien n'est envoyé.
- Android 11 (API 30) ou plus récent.
- Distribution hors Play Store (APK) : l'appli repose sur un service d'accessibilité, usage que
  Google Play refuse pour ce type d'application.

## Manager et Agent

AGAS est en deux parties, sur le modèle de ReVanced Manager et de ses patches :

| | Rôle | Mise à jour |
|---|---|---|
| **AGAS Manager** (ce dépôt) | L'appli installée : interface, réglages, journal, statistiques, service d'accessibilité, téléchargement et chargement de l'Agent | Rare : nouvel APK à installer |
| **[AGAS Agent](https://github.com/NzoSifou/AGAS-Agent)** | Toute la logique de passage des pubs : régies reconnues, recherche des croix, anti-détournement, mini-jeux, retour au jeu | Fréquente : téléchargé et chargé par le Manager, **sans réinstaller l'appli** |

Quand une nouvelle sorte de pub apparaît, il suffit de publier une nouvelle version de l'Agent :
le Manager la trouve sur GitHub (toutes les 12 h, ou à la demande dans *Réglages → Agent*), la
télécharge, vérifie sa signature et la charge à chaud. Pas d'écran d'installation Android ;
l'accessibilité et les réglages du téléphone restent en place.

Sécurité et robustesse :

- **Signature** : le Manager ne charge un Agent que s'il est signé par la clé de publication
  d'AGAS (l'Agent lit tout l'écran : rien d'autre n'est accepté).
- **Contrat versionné** (`agent-api/`) : un Agent qui demande un Manager plus récent est refusé,
  et le Manager propose de se mettre à jour.
- **Version intégrée** : le Manager embarque une copie de l'Agent (`assets/agent/`), utilisée hors
  ligne et en secours.
- **Retour arrière** : un Agent qui fait planter AGAS deux fois de suite est écarté
  automatiquement ; on peut aussi revenir à la version intégrée depuis les réglages.
- **Internet** : uniquement pour lire les releases GitHub d'AGAS Agent et d'AGAS Manager et
  télécharger l'Agent. Rien n'est envoyé.

## Fonctionnement

AGAS est un **service d'accessibilité**. L'Agent lit la structure de l'écran (l'« arbre
d'accessibilité »), pas les pixels :

1. **Détection de la pub** : ouverture d'un écran appartenant à une régie publicitaire connue
   (AdMob, AppLovin, Unity, ironSource, Vungle, Mintegral, Moloco, Fyber, BidMachine, Pangle…).
2. **Recherche du bouton de fermeture** : textes et descriptions (« Close », « Skip », « Fermer »,
   « Next »…), identifiants, croix dessinées en SVG, petites icônes dans un coin. Les boutons
   d'installation ou de boutique sont exclus.
3. **Clic** dès que le bouton apparaît et devient cliquable.
4. **Vérification** : si un clic ouvre le Play Store, AliExpress, un navigateur… (fausse croix), AGAS
   revient à la pub, évite cet endroit et mémorise le piège pour les pubs suivantes.

Le détail (récompenses, mini-jeux, redirections) est décrit dans le dépôt
[AGAS Agent](https://github.com/NzoSifou/AGAS-Agent).

## Installation

1. Télécharger **AGAS Manager** depuis la page
   [Releases](https://github.com/NzoSifou/AGAS-Manager/releases) et l'installer. L'Agent n'est
   pas à installer : il est intégré, puis mis à jour par le Manager. Une version installée depuis
   Android Studio (signature de debug) doit d'abord être désinstallée : Android refuse
   d'installer une appli signée par une autre clé par-dessus.
2. Ouvrir AGAS, puis **« Ouvrir l'accessibilité »** et activer **« AGAS – Passe-pub »**.
   - Option grisée ? Android bloque l'accessibilité des applis installées hors Play Store :
     *Infos de l'appli* → menu ⋮ → **« Autoriser les paramètres restreints »**, puis réessayer.
3. **Xiaomi / HyperOS** (et autres surcouches agressives) : sans ces réglages, le système gèle AGAS
   quelques secondes après qu'il passe en arrière-plan.
   - Économiseur de batterie → **« Pas de restrictions »** ;
   - **Démarrage automatique** activé ;
   - dans les applis récentes, **verrouiller** AGAS (appui long → cadenas).

   La carte « Fiabilité » de l'onglet Accueil ouvre directement ces écrans.

## Réglages

| Réglage | Par défaut | Rôle |
|---|---|---|
| Passer les pubs automatiquement | activé | Interrupteur général (arrête l'Agent) |
| Mode test | désactivé | L'Agent détecte et journalise ce qui serait cliqué, sans cliquer |
| Mettre à jour l'Agent automatiquement | activé | Installe les nouvelles versions de l'Agent dès leur publication |

Les autres réglages (anti-détournement, mini-jeux, bouton boutique…) sont **déclarés par l'Agent**
et affichés tels quels : une nouvelle version de l'Agent peut en ajouter sans mise à jour du
Manager. Leur valeur est conservée d'une version de l'Agent à l'autre.

## Développement

Les deux dépôts se clonent côte à côte : l'Agent compile contre le contrat (`agent-api/`) de ce
dépôt.

```bash
git clone https://github.com/NzoSifou/AGAS-Manager.git
git clone https://github.com/NzoSifou/AGAS-Agent.git
```

```bash
./gradlew assembleDebug        # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests unitaires (versions, releases GitHub)
./gradlew assembleRelease      # APK signé : app/build/outputs/apk/release/app-release.apk
```

La release est signée avec la clé décrite dans `keystore.properties` à la racine du projet
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Ce fichier et la clé ne sont jamais
commités ; sans eux, `assembleRelease` produit un APK non signé. L'Agent est signé avec la même clé.

**Version intégrée de l'Agent** : avant une release du Manager, copier l'APK de release de l'Agent
dans `app/src/main/assets/agent/agas-agent.apk`.

**Contrat Manager ↔ Agent** (`agent-api/`) : ne jamais modifier ni supprimer un élément existant
d'`Agent`, `AgentHost` ou `AgentSetting` ; en ajouter, et augmenter `AgentApi.VERSION`. Un Agent
compilé contre la version N ne se charge que dans un Manager qui implémente N ou plus.

Diagnostic sur appareil :

- **Journal** : visible dans l'appli (bouton « Partager ») et dans
  `Android/data/fr.nzosifou.agas/files/agas_log.txt`.
- **Structures des pubs non résolues** (arbre + capture PNG) :
  `Android/data/fr.nzosifou.agas/files/dumps/`.
- **Enregistrement à la demande** (version debug uniquement) :
  `adb shell am broadcast -a fr.nzosifou.agas.DUMP -p fr.nzosifou.agas`.
  Ne pas utiliser `uiautomator dump` : il suspend les services d'accessibilité.
- **Essayer un Agent de développement** (version debug uniquement) : copier l'APK debug de l'Agent
  dans `Android/data/fr.nzosifou.agas/files/agent-dev.apk`, puis
  `adb shell am broadcast -a fr.nzosifou.agas.LOAD_AGENT -p fr.nzosifou.agas`.

Structure du code (`app/src/main/java/fr/nzosifou/agas/`) :

| Dossier | Contenu |
|---|---|
| `service/` | `AdSkipperService` : service d'accessibilité, au premier plan, qui transmet tout à l'Agent |
| `runtime/` | `AgentRuntime` (chargement, démarrage, remplacement à chaud, protection contre les plantages), `AgentStore` (versions disponibles, signature), `AgentUpdater` (releases GitHub) |
| `data/` | Réglages, statistiques, journal, état en direct |
| `ui/` | Interface Jetpack Compose : `AgasApp` (onglets), `SetupScreen`, `HomeTab`, `SettingsTab`, `AgentSection`, `LogTab`, composants et thème Nocturne |
| `../agent-api/` | Contrat entre le Manager et l'Agent |

## Changelog

### [1.0.0] — non publiée

Première version, en deux parties : AGAS Manager et
[AGAS Agent](https://github.com/NzoSifou/AGAS-Agent) (voir son changelog pour le passage des pubs).

**Manager et Agent**

- Logique de passage des pubs déplacée dans AGAS Agent, chargé à la volée par le Manager.
- Mise à jour de l'Agent depuis les releases GitHub, sans réinstaller l'appli : automatique
  (toutes les 12 h) ou à la demande, avec vérification de la signature et du contrat.
- Version de l'Agent intégrée au Manager pour fonctionner hors ligne ; retour automatique à la
  version précédente si un Agent fait planter AGAS, ou à la demande.
- Réglages déclarés par l'Agent, affichés et mémorisés par le Manager.
- Signalement des nouvelles versions d'AGAS Manager.

**Fiabilité**

- Service au premier plan et assistant de réglages Xiaomi / HyperOS contre la mise en veille.
- Journal persistant et partageable, enregistrement des pubs non résolues (structure et capture).

**Interface et identité**

- Interface Jetpack Compose au design system sombre « Nocturne » (icônes Phosphor), en trois
  onglets : **Accueil** (état en direct de la pub en cours, statistiques, carte « Fiabilité » à
  cocher, mises à jour, activité récente), **Réglages** (regroupés par usage, section Agent) et
  **Journal** (filtres Tout / Actions / Alertes, partage).
- Écran de première configuration tant que le service d'accessibilité n'est pas activé.
- Messages éphémères quand une pub est passée ou qu'une fausse croix est évitée.
- Logo « Avance rapide auto » : icône adaptative (fond et premier plan séparés), icône monochrome
  pour les icônes à thème (Android 13+), icône de notification, écran de démarrage.
