# CrazyMineSize 📏

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Paper](https://img.shields.io/badge/Platform-Paper%201.20.5%20--%201.21.x-brightgreen.svg)](https://papermc.io)
[![Java: 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)

**CrazyMineSize** ist ein modernes, leichtgewichtiges und hochgradig anpassbares Paper-Plugin, mit dem Spieler ihre Körpergröße (Hitbox, Reichweite, Schritt- und Sprunghöhe) über das native Minecraft `Attribute.SCALE` verändern können.

Entwickelt für das [CrazyMine.de](https://crazymine.de) Minecraft-Netzwerk.

---

## ✨ Features

- 🎮 **Natives Minecraft 1.20.5+ / 1.21.x Attribute.SCALE:** Keine Glitches, keine verbuggten Entities – echte Skalierung von Hitbox, Blickhöhe, Reichweite und Schrittgröße.
- 🖼️ **Interaktives GUI-Menü (`/size`):** Übersichtliche Auswahl mit konfigurierbaren Icons, aktuellen Größen-Indikatoren und Ein-Klick-Reset.
- 💬 **100% anpassbare `messages.yml`:** Jede Nachricht, jeder Präfix, Tooltip und Menütitel unterstützt modernes **MiniMessage** (Gradients, Hex-Farben, Klick-Events).
- ⚙️ **Umfangreiche `config.yml`:**
  - Frei definierbare Presets (Name, Skalierungsfaktor, Slot, Icon, Berechtigung).
  - Min- und Max-Limits für manuelle Eingaben.
  - Optionale Persistenz (Größe bleibt über Server-Neustarts und Reconnects erhalten).
  - Unterstützung für geteilte Netzwerkspeicher (Proxy / Multi-Server).
  - Konfigurierbare Sound- und Partikeleffekte beim Ändern der Größe.
- 🔒 **Feingranulares Berechtigungssystem:** Eigene Permissions für jedes Preset, freie Zahlenwerte (`crazymine.size.custom`), Limit-Bypass, Größenänderung anderer Spieler und Server-Standardgröße.

---

## 📋 Befehle & Berechtigungen

### Befehle

| Befehl | Beschreibung | Berechtigung |
| :--- | :--- | :--- |
| `/size` *(oder `/groesse`, `/scale`)* | Öffnet das grafische Größen-Menü | `crazymine.size.use` |
| `/size <preset>` | Wählt ein vorkonfiguriertes Preset (z. B. `klein`, `normal`, `gross`) | Preset-abhängig |
| `/size <wert>` | Setzt eine freie Skalierung (z. B. `0.75` oder `1.4`) | `crazymine.size.custom` |
| `/size reset` | Setzt die eigene Größe auf `1.0x` zurück | `crazymine.size.use` |
| `/size <spieler> <größe>` | Ändert die Größe eines anderen Spielers | `crazymine.size.others` |
| `/size server <größe>` | Ändert die Standardgröße des gesamten Servers | `crazymine.size.server` |
| `/size reload` | Lädt `config.yml` und `messages.yml` im laufenden Betrieb neu | `crazymine.size.admin` |
| `/size help` | Zeigt die Befehlsübersicht an | `crazymine.size.use` |

### Berechtigungen

| Berechtigung | Beschreibung | Standard |
| :--- | :--- | :--- |
| `crazymine.size.use` | Erlaubt `/size` und das Öffnen des Menüs | Jeder |
| `crazymine.size.small` | Erlaubt das Preset *Klein* (0.5x) | OP |
| `crazymine.size.normal` | Erlaubt das Preset *Normal* (1.0x) | Jeder |
| `crazymine.size.large` | Erlaubt größere Presets (*Etwas größer*, *Groß*) | OP |
| `crazymine.size.custom` | Erlaubt freie Zahlenwerte als Größe | OP |
| `crazymine.size.bypass` | Erlaubt das Überschreiten der Min-/Max-Grenzen | OP |
| `crazymine.size.others` | Erlaubt das Ändern der Größe anderer Spieler | OP |
| `crazymine.size.server` | Erlaubt das Ändern der globalen Server-Größe | OP |
| `crazymine.size.admin` | Voller Adminzugriff und Konfigurations-Reload | OP |
| `crazymine.size.*` | Beinhaltet alle oben genannten Berechtigungen | OP |

---

## 🛠️ Konfiguration

### `config.yml`
```yaml
# Ist das System aktiv?
enabled: true

# Erfordern Presets Permissions (crazymine.size.<preset>)?
require-permission: true

# Erlaubter Rahmen für manuelle Zahlen (/size 0.5)
min-scale: 0.2
max-scale: 2.5

# Standard-Größe beim ersten Join
server-scale: 1.0
force-server-scale: false

# Speicherung über Reconnects hinweg
storage:
  persist: true
  # Leer lassen für lokale Datei (plugins/CrazyMineSize/sizes.yml)
  custom-file-path: ""

# Menü-Design
gui:
  size: 27
  filler:
    enabled: true
    material: "GRAY_STAINED_GLASS_PANE"
  info-item:
    enabled: true
    slot: 4
    material: "COMPASS"
  reset-item:
    enabled: true
    slot: 26
    material: "REDSTONE"

# Presets
presets:
  klein:
    scale: 0.5
    title: "<gradient:#38bdf8:#818cf8><bold>Klein</bold></gradient>"
    description: "<gray>Schrumpfe auf halbe Größe (0.5x)</gray>"
    permission: "crazymine.size.small"
    material: "BABY_TURTLE_SPAWN_EGG"
    slot: 11
  normal:
    scale: 1.0
    title: "<green><bold>Normal</bold></green>"
    description: "<gray>Standard-Größe eines Spielers (1.0x)</gray>"
    permission: "crazymine.size.normal"
    material: "PLAYER_HEAD"
    slot: 13
```

### `messages.yml`
Unterstützt vollständiges Adventure **MiniMessage** Styling:
```yaml
prefix: "<gradient:#ff7a18:#ffd166><bold>CrazyMine</bold></gradient> <dark_gray>»</dark_gray> "
size-set: "<gray>Deine Größe wurde auf <yellow><size>x</yellow> gesetzt!</gray>"
size-reset: "<gray>Deine Größe wurde auf <green>Normal (1.0x)</green> zurückgesetzt.</gray>"
no-permission: "<red>Dazu hast du keine Berechtigung!</red>"
# ... (alle weiteren Menütexte und Benachrichtigungen)
```

---

## 🚀 Kompilieren & Installieren

### Voraussetzungen
- Java 21+
- Paper oder Purpur (1.20.5 oder 1.21+)

### Build aus dem Quellcode
```bash
git clone https://github.com/CrazyMine-de/crazymine-size.git
cd crazymine-size
./gradlew shadowJar
```
Die fertige JAR-Datei befindet sich in `build/libs/crazymine-size.jar`.

---

## 📄 Lizenz
Dieses Projekt ist unter der [MIT-Lizenz](LICENSE) lizenziert.
