# Changelog — eduwisp-android-dpc

## Non rilasciato / Unreleased

- 0.3.0 — App (ADR-0019): elenco di app consentite (le altre app con icona sono sospese, con il messaggio della scuola), app vietate dalla piattaforma sempre bloccate, sblocchi temporanei richiusi alla scadenza anche senza rete, app obbligatorie segnalate allo studente con il link allo store, installazioni da fonti sconosciute vietate, VPN e DNS privato non configurabili. Le app di sistema essenziali e la schermata Home non vengono mai sospese. Provato sull'emulatore Android 15 con il server demo. / *0.3.0 — App allowlist, forbidden apps, timed unlocks enforced offline, required apps, VPN and private DNS locked.*
- 0.2.0 — Studenti maggiorenni (ADR-0018): se la scuola lo chiede, la schermata mostra la scelta sull'accesso dei familiari; la scelta si conferma con il blocco schermo (senza blocco schermo non si può scegliere), resta in attesa se il server non è raggiungibile e si invia con il rapporto di stato. / *0.2.0 — Adult students choose on the device, confirmed with the screen lock, whether family members keep access.*
- Prototipo v0 in Kotlin (minSdk 29, targetSdk 36): device owner con provisioning QR (`GET_PROVISIONING_MODE`, `ADMIN_POLICY_COMPLIANCE`), registrazione, lettura della policy e rapporto di stato con il protocollo v0. / *Kotlin prototype v0: device owner with QR provisioning, enrollment, policy and status with protocol v0.*
- Impostazioni `camera_disabled` e `screen_capture_disabled`; quelle sconosciute o non applicabili vengono segnalate al server. / *Settings `camera_disabled`, `screen_capture_disabled`; unknown or failed ones are reported.*
- Sincronizzazione periodica con WorkManager, un ciclo alla volta; funziona offline con l'ultima policy applicata. / *Periodic sync with WorkManager, one cycle at a time; offline-safe.*
- Credenziale del dispositivo cifrata con l'Android Keystore; backup disattivato; solo HTTPS. / *Device credential encrypted with Android Keystore; backups disabled; HTTPS only.*
- Interfaccia in italiano (predefinita) e inglese con l'avviso di dispositivo gestito e l'attribuzione «Basato su EduWISP». / *Italian (default) and English UI with managed-device notice and attribution.*
- Test sulla JVM con gli esempi ufficiali del protocollo; CI con build, test, lint e controllo di allineamento degli esempi. / *JVM tests on official protocol examples; CI with build, tests, lint and example sync check.*
- Creazione del repository (fase di progettazione). / *Repository created (design phase).*
- Licenza AGPL-3.0-or-later con termini aggiuntivi (sezione 7), publiccode.yml e controllo DCO. / *AGPL-3.0-or-later licence with additional terms, publiccode.yml and DCO check.*
