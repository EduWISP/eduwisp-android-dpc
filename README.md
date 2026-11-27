<p align="center"><img src="https://raw.githubusercontent.com/EduWISP/.github/main/branding/eduwisp-mark.svg" alt="EduWISP" height="72"></p>

# eduwisp-android-dpc

**DPC Android** · *Android DPC*

Primo agente di EduWISP: agente Android open source in modalità device owner: applica policy, restrizioni, kiosk, aggiornamenti e fasce orarie anche offline.

> 🚧 **Stato: prototipo v0.** Registrazione con il [protocollo v0](https://github.com/EduWISP/eduwisp-protocol), sincronizzazione periodica e applicazione di `camera_disabled` e `screen_capture_disabled` come device owner. Provato sull'emulatore Android 15 con il server demo; il provisioning con QR code su un tablet reale è ancora da verificare. Progettazione: [matrice di compatibilità](https://eduwisp.org/docs/moduli/android-matrice-compatibilita/).

🇬🇧 *Open-source Android agent running as device owner: enforces policies, restrictions, kiosk, updates and time slots, offline too. **Status: prototype v0**: enrollment with protocol v0, periodic sync, `camera_disabled` and `screen_capture_disabled`; tested on the Android 15 emulator against the demo server. QR provisioning on real hardware still to be verified.*

## Compilare / Build

Requisiti: JDK 17+, Android SDK 36. Da Android 10 (API 29). / *Requires JDK 17+, Android SDK 36; runs on Android 10+.*

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Prova sull'emulatore, struttura del codice e limiti: [`docs/developer/`](docs/developer/). / *Emulator steps and code layout in `docs/developer/`.*

## Documentazione / Documentation

- Progettazione / Design: <https://eduwisp.org/docs/moduli/android-matrice-compatibilita/>
- Mappa dei repository / Repository map: <https://eduwisp.org/docs/architettura/repository/>
- Documentazione del modulo / Module docs: [`docs/user/`](docs/user/) · [`docs/developer/`](docs/developer/)

## Dipendenze / Dependencies

[`eduwisp-protocol`](https://github.com/EduWISP/eduwisp-protocol)

## Privacy

Dati personali previsti e regole: [PRIVACY.md](PRIVACY.md). Il rispetto della normativa su privacy e minori è un vincolo bloccante ([ADR-0008](https://eduwisp.org/docs/decisioni/0008-conformita-normativa/)).

## Responsabile del modulo / Module owner

Cerchiamo persone che vogliano prendere in carico questo modulo! Proponiti in [Discussions](https://github.com/EduWISP/docs/discussions). / *Want to own this module? Say hi in Discussions.*

## Contribuire / Contributing

Vedi [CONTRIBUTING](https://github.com/EduWISP/.github/blob/main/CONTRIBUTING.md) e il [Codice di condotta](https://github.com/EduWISP/.github/blob/main/CODE_OF_CONDUCT.md). **Mai dati personali reali** di studenti, famiglie o personale.

## Licenza / License

**AGPL-3.0-or-later**, con termini aggiuntivi di attribuzione e tutela del nome (sezione 7): vedi [LICENSE](LICENSE) e [NOTICE.md](NOTICE.md). Chi usa o modifica EduWISP deve sempre indicare «Basato su EduWISP» e non può usare il nome e il logo per versioni modificate. Licenza scelta secondo le [linee guida AgID](https://eduwisp.org/docs/decisioni/0006-licenza/) per il software usato via rete.

*AGPL-3.0-or-later with additional attribution and trademark terms (section 7): see LICENSE and NOTICE.md.*

I contributi richiedono il [DCO](https://github.com/EduWISP/.github/blob/main/CONTRIBUTING.md#dco) (`git commit -s`).
