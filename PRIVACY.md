# Privacy — eduwisp-android-dpc

Documento vivo: va aggiornato a ogni modifica che riguarda dati personali. Riferimenti: [Quadro normativo](https://eduwisp.org/docs/sicurezza-privacy/quadro-normativo/), [Requisiti di conformità](https://eduwisp.org/docs/requisiti/requisiti/#requisiti-di-conformità).

## Dati personali previsti

- Stato tecnico del dispositivo inviato al server (minimo necessario).
- Nessuna lettura di messaggi, foto, contenuti personali, nessun keylogging né cattura dello schermo, nessuna geolocalizzazione continua (RC-005).

## Dati trattati dal prototipo v0

Nessun dato personale dello studente: il DPC non conosce nome, classe o account di chi usa il dispositivo. L'associazione dispositivo-studente esiste solo sul server ([eduwisp-devices](https://github.com/EduWISP/eduwisp-devices)).

| Dato | Dove | Finalità | Conservazione | Chi vi accede |
|---|---|---|---|---|
| Indirizzo del server, identificativo del dispositivo | Dispositivo (`SharedPreferences` privata dell'app) | Collegarsi al server della scuola | Finché l'app è installata | Solo l'app |
| Credenziale del dispositivo | Dispositivo, cifrata con chiave non esportabile dell'Android Keystore | Autenticarsi al server | Finché l'app è installata | Solo l'app |
| Token di registrazione del QR code | Dispositivo | Prima registrazione | Cancellato subito dopo la registrazione, o se rifiutato | Solo l'app |
| Modello, versione di Android e dell'agente, impostazioni supportate | Inviati al server | Inventario tecnico e compatibilità delle policy | Secondo le regole del server | Amministratori della scuola |
| Versione della policy applicata, impostazioni non applicate | Inviati al server | Verifica della conformità | Secondo le regole del server | Amministratori della scuola |

Backup cloud e trasferimento tra dispositivi sono disattivati (`allowBackup=false`, `data_extraction_rules.xml`): la credenziale non lascia il dispositivo. Il traffico è solo HTTPS con le autorità di certificazione di sistema; l'HTTP in chiaro è consentito solo nella build di debug verso il PC di sviluppo (`10.0.2.2`).

**Base giuridica** e **informativa** sono in carico alla scuola titolare del trattamento ([ADR-0008](https://eduwisp.org/docs/decisioni/0008-conformita-normativa/)); vanno completate prima di qualsiasi uso con studenti reali, insieme alla DPIA.

---

🇬🇧 *Expected personal data:*

- *Technical device status sent to the server (minimum necessary).*
- *No reading of messages, photos or personal content, no keylogging or screen capture, no continuous location (RC-005).*

*Prototype v0 stores on the device only the server URL, device ID, the device credential (encrypted with a non-exportable Keystore key) and, until enrollment, the QR enrollment token. It sends model, Android and agent version, supported settings, applied policy version and non-applied settings. No student data. Backups and device transfer are disabled; HTTPS only.*
