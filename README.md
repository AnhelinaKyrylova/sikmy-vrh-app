# Šikmý vrh – klient-server Android aplikácia

Mobilná aplikácia v Kotline na simuláciu šikmého vrhu. Používateľ zadá počiatočnú rýchlosť a uhol výstrelu, trajektóriu vypočíta server (TCP sokety) a aplikácia ju zobrazí ako numerický výpis, graf y(t) a animáciu letu. Ak server nie je dostupný, aplikácia trajektóriu spočíta lokálne.

## Ukážky aplikácie

| Zadanie parametrov | Numerický výpis | Graf y(t) | Animácia |
|---|---|---|---|
| <img src="screen2.png" width="190"> | <img src="screen3.png" width="200"> | <img src="screen4.png" width="210"> | <img src="screen5.png" width="200"> |

## Funkcie

- Zadanie počiatočnej rýchlosti, uhla výstrelu a IP adresy servera
- Výpočet trajektórie na serveri (čas, vzdialenosť x, výška y)
- **Numerický výpis** hodnôt (čas, x, y) v zozname
- **Graf** závislosti výšky od času y(t) s hladkou krivkou, animáciou kreslenia a zoomom
- **Animácia** letu s mriežkou a popismi osí, čiarkovanou dráhou, stopou, označeným maximom a živými hodnotami (čas, x, y)
- Súhrn: doba letu, maximálna výška, dĺžka vrhu
- Záložný lokálny výpočet pri nedostupnom serveri (zdroj dát je farebne označený: fialová = server, oranžová = lokálny výpočet)

## Architektúra

```
┌──────────────────────┐   TCP/IP, port 8888   ┌──────────────────────┐
│  Android klient      │ ────────────────────► │  Kotlin server       │
│  (modul app)         │  rýchlosť, uhol       │  (modul Desktop)     │
│                      │ ◄──────────────────── │  DesktopServer.kt    │
└──────────────────────┘  body trajektórie     └──────────────────────┘
```

### Komunikačný protokol

Údaje sa prenášajú binárne cez `DataInputStream` / `DataOutputStream`:

| Smer | Údaj | Typ |
|---|---|---|
| Klient → server | počiatočná rýchlosť, uhol | 2× `double` |
| Server → klient | počet bodov | `int` |
| Server → klient | body trajektórie (t, x, y) | trojice `double` |

Server kontroluje vstupy (neplatné hodnoty vráti ako 0 bodov) a posiela aj presný bod dopadu. Klient má časový limit pripojenia 2 s a čítania 5 s.

## Použité technológie

- **Jazyk:** Kotlin (klient aj server)
- **Sieť:** `Socket`, `ServerSocket`, TCP/IP; sieťová komunikácia beží v samostatnom vlákne (`Thread` + `runOnUiThread`), aby nevznikla `NetworkOnMainThreadException`
- **Prenos medzi obrazovkami:** `Intent` s `Parcelable` (`TrajectoryPointParcel`)
- **Výpis:** `ListView` + `ArrayAdapter<String>`, formátovanie čísel cez `DecimalFormat`
- **Graf:** [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) v3.1.0 (`LineChart`)
- **Animácia:** vlastný `View` (`TrajectoryAnimationView`) kreslený cez `Canvas`, `Paint`, `Path`, pohyb riadi `ValueAnimator`
- **UI:** viacero aktivít, ConstraintLayout, Material komponenty (`MaterialCardView`), `SpannableString`, `Toast`, edge-to-edge a `WindowInsets`

## Štruktúra projektu

| Súbor | Popis |
|---|---|
| `app/.../MainActivity.kt` | zadanie parametrov, komunikácia so serverom (`calculateOnServer()`), lokálny výpočet (`calculateLocally()`) |
| `app/.../TrajectoryListActivity.kt` | numerický výpis |
| `app/.../GraphActivity.kt` | graf y(t) |
| `app/.../AnimationActivity.kt` | obrazovka animácie so živými hodnotami |
| `app/.../TrajectoryAnimationView.kt` | vlastný view s animáciou |
| `Desktop/.../DesktopServer.kt` | serverová časť (konzolová aplikácia) |

## Požiadavky

- Android Studio s Android SDK a emulátorom (alebo reálny telefón)
- Knižnica MPAndroidChart sa stiahne automaticky cez Gradle (závislosť v `build.gradle.kts`, repozitár JitPack v `settings.gradle.kts`)

## Spustenie

1. Otvorte projekt v Android Studiu a spravte **Sync Now**.
2. Spustite mobilnú aplikáciu na emulátore alebo telefóne.
3. Spustite server (`DesktopServerKt`, modul Desktop).
4. V aplikácii zadajte IP adresu servera (v emulátore `10.0.2.2`), rýchlosť a uhol a kliknite na **Calculate Trajectory**.
5. Výsledok zobrazíte tlačidlami **List Data**, **Show Graph** a **Show Animation**.

> **Poznámka:** `targetSdk` je nastavený na 36, pretože pri hodnote 37 na emulátore s Androidom 17 spojenie so serverom nefungovalo.
