# AGAS — Android Games Ads Skipper

AGAS passe automatiquement les publicités plein écran des jeux mobiles : il attend que le bouton
de fermeture apparaisse et appuie dessus à ta place, y compris pour les pubs en plusieurs étapes
(croix, puis « Skip », puis écran de fin…).

AGAS **ne bloque aucune publicité** : la pub s'affiche normalement, elle est comptabilisée et le
développeur du jeu est rémunéré. Les récompenses des pubs « récompensées » sont conservées.

- Aucune IA, aucun serveur : tout se passe sur le téléphone, rien n'est envoyé.
- Android 11 (API 30) ou plus récent.
- Distribution hors Play Store (APK) : l'appli repose sur un service d'accessibilité, usage que
  Google Play refuse pour ce type d'application.

## Fonctionnement

AGAS est un **service d'accessibilité**. Il lit la structure de l'écran (l'« arbre
d'accessibilité »), pas les pixels :

1. **Détection de la pub** : ouverture d'un écran appartenant à une régie publicitaire connue
   (AdMob, AppLovin, Unity, ironSource, Vungle, Mintegral, Moloco, Fyber, BidMachine, Pangle…).
2. **Recherche du bouton de fermeture** : textes et descriptions (« Close », « Skip », « Fermer »,
   « Next »…), identifiants (`close_button`, `skip`…), croix dessinées en SVG, petites icônes dans
   un coin. Les boutons d'installation ou de boutique sont exclus.
3. **Clic** dès que le bouton apparaît et devient cliquable (analyse toutes les 400 ms et à chaque
   changement de l'écran).
4. **Vérification** : si un clic ouvre le Play Store, AliExpress, un navigateur… (fausse croix), AGAS
   revient à la pub, évite cet endroit et mémorise le piège pour les pubs suivantes.

Cas particuliers gérés :

- **Récompenses** : attente des comptes à rebours (« Reward in 25 s »), et clic sur « Reprendre »
  si une fenêtre « vous allez perdre votre récompense » apparaît.
- **Mini-jeux (playables)** : un seul appui de « réveil » pour faire apparaître le bouton « Next »
  (jamais un second, qui ouvrirait la boutique), et, en dernier recours, utilisation du bouton
  « Google Play » / « Ouvrir la boutique » quand c'est la seule sortie, suivie de la fermeture de la
  boutique.
- **Redirections automatiques** : si la pub ouvre d'elle-même le Play Store, AGAS le referme.

Les règles de détection (régies, libellés, pièges connus…) sont dans
[`app/src/main/assets/ad_rules.json`](app/src/main/assets/ad_rules.json), séparées du code.

## Installation

1. Installer l'APK (`app-debug.apk` ou version de release).
2. Ouvrir AGAS, puis **« Ouvrir l'accessibilité »** et activer **« AGAS – Passe-pub »**.
   - Option grisée ? Android bloque l'accessibilité des applis installées hors Play Store :
     *Infos de l'appli* → menu ⋮ → **« Autoriser les paramètres restreints »**, puis réessayer.
3. **Xiaomi / HyperOS** (et autres surcouches agressives) : sans ces réglages, le système gèle AGAS
   quelques secondes après qu'il passe en arrière-plan.
   - Économiseur de batterie → **« Pas de restrictions »** ;
   - **Démarrage automatique** activé ;
   - dans les applis récentes, **verrouiller** AGAS (appui long → cadenas).

   La carte « Fiabilité » de l'appli ouvre directement ces écrans.

## Réglages

| Réglage | Par défaut | Rôle |
|---|---|---|
| Passer les pubs automatiquement | activé | Interrupteur général |
| Mode test | désactivé | Détecte et journalise ce qui serait cliqué, sans cliquer |
| Anti-détournement | activé | Revient à la pub si un clic ou la pub ouvre une autre appli, et mémorise les fausses croix |
| Boutons sans libellé | activé | Après 8 s, essaie les petites icônes dans un coin |
| Réveiller les mini-jeux | activé | Un seul appui pour faire apparaître « Next » sur un mini-jeu |
| Sortir par le bouton boutique | activé | Utilise « Google Play » / « Ouvrir la boutique » quand c'est la seule sortie (compte comme un clic sur la pub) |
| Touche Retour en dernier recours | désactivé | Après 45 s sans bouton trouvé (peut faire perdre une récompense) |
| Enregistrer les pubs non résolues | activé | Sauvegarde la structure et une capture des pubs non résolues |

## Développement

```bash
./gradlew assembleDebug        # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests unitaires (règles, anti-détournement, croix SVG)
```

Diagnostic sur appareil :

- **Journal** : visible dans l'appli (bouton « Partager ») et dans
  `Android/data/fr.nzosifou.agas/files/agas_log.txt`.
- **Structures des pubs non résolues** (arbre + capture PNG) :
  `Android/data/fr.nzosifou.agas/files/dumps/`.
- **Enregistrement à la demande** (version debug uniquement) :
  `adb shell am broadcast -a fr.nzosifou.agas.DUMP -p fr.nzosifou.agas`.
  Ne pas utiliser `uiautomator dump` : il suspend les services d'accessibilité.

Structure du code (`app/src/main/java/fr/nzosifou/agas/`) :

| Dossier | Contenu |
|---|---|
| `service/` | `AdSkipperService` : suivi des pubs, boucle d'analyse, clics, retour au jeu |
| `detection/` | `CloseButtonFinder` (analyse de l'écran), `SvgIcons` (croix SVG), `TreeDumper` |
| `guard/` | `HijackGuard` : détection des détournements après un clic |
| `rules/` | Chargement de `ad_rules.json` |
| `data/` | Réglages, statistiques, journal, mémoire des pièges |
| `ui/` | Interface Jetpack Compose |

## Changelog

### [1.0] — non publiée

Première version.

**Détection et fermeture**

- Service d'accessibilité qui détecte les pubs plein écran d'une quarantaine de régies publicitaires.
- Recherche du bouton de fermeture par texte, description, identifiant, croix SVG et icônes sans
  libellé ; boutons d'installation et de boutique exclus.
- Pubs en plusieurs étapes (vidéo → « Skip » → écran de fin) et popups dans la pub.
- Contenu des pubs HTML (WebView) lu en direct : cache d'accessibilité désactivé, abonnement à tous
  les événements pour que Chromium expose la page.
- Clic d'accessibilité ou appui simulé selon le cas ; jamais d'appui simulé sur un bouton pas encore
  cliquable (il traverserait jusqu'à la pub).

**Protection**

- Anti-détournement : retour à la pub si un clic ou la pub elle-même ouvre le Play Store, un
  navigateur ou une autre appli, y compris à travers des redirections en chaîne.
- Mémoire des pièges : un bouton qui a ouvert la boutique est évité pour ce type de pub.
- Protection des récompenses (comptes à rebours, fenêtre « récompense perdue »).

**Mini-jeux**

- Appui de réveil unique pour faire apparaître « Next » (pas pendant une vidéo ni un compte à
  rebours).
- Bouton de sortie « Google Play » / « Ouvrir la boutique » en dernier recours, puis fermeture de la
  popup du Play Store.

**Fiabilité**

- Service au premier plan et assistant de réglages Xiaomi / HyperOS contre la mise en veille.
- Journal persistant et partageable, enregistrement des pubs non résolues (structure et capture).

**Interface et identité**

- Interface Jetpack Compose : état du service, réglages, statistiques, journal.
- Logo « Avance rapide auto » : icône adaptative (fond et premier plan séparés), icône monochrome
  pour les icônes à thème (Android 13+), icône de notification, écran de démarrage.
