# DPC Android — documentazione per sviluppatori

🇬🇧 *English summary at the end.*

## Struttura

| Percorso | Contenuto |
|---|---|
| `protocol/` | Messaggi del [protocollo v0](https://github.com/EduWISP/eduwisp-protocol) e client HTTP (`HttpURLConnection`, nessuna libreria esterna) |
| `policy/` | `PolicyApplier`: traduce le impostazioni indipendenti dalla piattaforma in chiamate a `DevicePolicyManager` |
| `store/` | Stato dell'agente in `SharedPreferences`; la credenziale del dispositivo è cifrata con AES-GCM e chiave non esportabile nell'Android Keystore |
| `sync/` | `Agent` (un ciclo: registrazione, policy, applicazione, stato) e `SyncWorker` (WorkManager, periodico e immediato, un ciclo alla volta) |
| `provisioning/` | Activity richieste dal provisioning QR (`GET_PROVISIONING_MODE`, `ADMIN_POLICY_COMPLIANCE`) e lettura degli extra `org.eduwisp.SERVER_URL` / `org.eduwisp.ENROLLMENT_TOKEN` |
| `ui/` | Schermata informativa per chi usa il dispositivo |
| `src/debug/` | Solo build di debug: `testOnly`, HTTP verso `10.0.2.2`, receiver per configurare l'agente da adb |

Dipendenze: solo AndroidX WorkManager (Apache-2.0). Niente Google Play Services né Firebase ([ADR-0009](https://eduwisp.org/docs/decisioni/0009-solo-software-libero/), [ADR-0013](https://eduwisp.org/docs/decisioni/0013-stack-tecnologico/)).

Impostazioni supportate in v0: `camera_disabled`, `screen_capture_disabled`. Un'impostazione assente dalla policy torna al valore predefinito di Android; una sconosciuta viene segnalata come `unsupported`, un valore non valido come `failed`, e se l'app non è device owner come `pending`.

## Requisiti

- JDK 17 o successivo (CI: Temurin 21)
- Android SDK con platform 36 e build-tools 36 (`local.properties` con `sdk.dir=...`)
- Emulatore con un'immagine **AOSP senza account Google**, per poter impostare il device owner da adb

## Compilare e provare

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

I test sulla JVM usano gli esempi ufficiali del protocollo, copiati in `app/src/test/resources/protocol/v0`. La CI controlla che coincidano con quelli di `eduwisp-protocol`: se cambi il protocollo, aggiorna anche la copia.

## Prova sull'emulatore

Il device owner si può impostare da adb solo su un dispositivo appena azzerato e senza account.

```sh
emulator -avd eduwisp-aosp-35 -wipe-data -no-snapshot &
adb install -t app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner org.eduwisp.dpc/.DpcReceiver

# Simula gli extra del QR code (solo build di debug, solo da adb).
# Il token si crea nell'admin del server demo: Dispositivi → Token di registrazione.
adb shell am broadcast -n org.eduwisp.dpc/.debug.DebugConfigReceiver \
    --es org.eduwisp.SERVER_URL https://demo.eduwisp.org \
    --es org.eduwisp.ENROLLMENT_TOKEN <token>

# Verifica delle restrizioni applicate
adb shell dumpsys device_policy | grep -E "no_camera|screenCaptureDisabled"

# Rimuovere il device owner (possibile solo perché la build di debug è testOnly)
adb shell dpm remove-active-admin org.eduwisp.dpc/.DpcReceiver
```

Per un server di sviluppo sul proprio PC usare `http://10.0.2.2:8000`: l'HTTP in chiaro è consentito solo verso quell'indirizzo e solo nella build di debug.

## Provisioning con QR code

Il contenuto del QR code è generato dal server (`eduwisp_devices.platforms.android`). Il flusso è stato provato solo in parte: sul tablet di prova va verificato il blocco di Play Protect per i DPC non approvati ([#3](https://github.com/EduWISP/eduwisp-android-dpc/issues/3)).

## Ancora da fare

- Firma della release e build riproducibili (F-Droid)
- Notifiche push con UnifiedPush al posto del solo polling
- Azioni alla dismissione del dispositivo (oggi l'agente smette solo di sincronizzarsi)
- Altre impostazioni: app consentite, kiosk, fasce orarie, aggiornamenti di sistema

---

🇬🇧 *Developer notes. Layout: `protocol/` (v0 messages, plain HTTP client), `policy/` (maps platform-independent settings to `DevicePolicyManager`), `store/` (state; device credential encrypted with a non-exportable Android Keystore key), `sync/` (one cycle at a time via WorkManager), `provisioning/` (QR provisioning activities and extras), `ui/`, `src/debug/` (testOnly, cleartext to `10.0.2.2`, adb configuration receiver). Only dependency: AndroidX WorkManager. Build and test with `./gradlew testDebugUnitTest lintDebug assembleDebug`; emulator steps above use an AOSP image without Google accounts.*
