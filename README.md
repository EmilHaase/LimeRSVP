# LimeRSVP

A speed reader for Android. Words appear one at a time, right where your eyes already are, with
the letter to look at lit up. Hold your finger on the screen to read; lift it and everything stops.

Open source, no ads, no tracking, and no internet permission: your books never leave your phone.

## Features

- **Hold to read.** Reading starts gently and eases up to your speed; lifting stops on the same frame.
- **Smart timing.** Long, rare and unfamiliar words stay longer, short words pass quickly, and
  every comma, sentence and paragraph gets its own pause.
- **Go back easily.** A quick tap returns to the start of the sentence. Two fingers sideways open
  the page and move through the text like a cursor.
- **A speed knob.** Turn it, or slide two fingers up and down. 600 words a minute sits at the top.
- **Real books.** EPUB, PDF, TXT and HTML with their actual chapters. Covers, copyright pages and
  tables of contents are skipped; footnotes, page numbers and running headers are left out.
- **Every language.** Czech, Polish, German, French and every other Latin-script language, Cyrillic,
  Greek, Arabic and Hebrew, and Chinese, Japanese and Thai, which have no spaces between words.
- **Easy on the eyes.** Pure black for OLED screens, night red that keeps your night vision,
  amber, dim and day themes. The default font is Atkinson Hyperlegible Next.
- **Where you are.** Chapter, progress and an honest estimate of the time left, at your speed.
- **Statistics.** Real reading speed, time read per day, streaks and an optional daily goal.
- **Your way.** Volume keys can change the speed or read while held; vibration is tuned to each phone.

Works on Android 9 and newer. The app is in English and Czech.

## Gestures

| Do this | It does |
|---|---|
| Hold one finger anywhere | Read |
| Lift | Stop |
| Tap | Back to the start of the sentence |
| Two fingers sideways | Move through the text; the page opens |
| Two fingers up or down | Change the speed |
| Turn the knob | Change the speed (hold it to return to 600) |
| Drag along the progress line | Travel through the book |

## Building

```
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

JDK 17 or newer and the Android SDK are needed. How the app is put together, and why it behaves
the way it does, is described in [docs/DESIGN.md](docs/DESIGN.md).

## License

Copyright © 2026 Emil Haase

LimeRSVP is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License](LICENSE), version 3 or any later version, with the
[additional terms](ADDITIONAL_TERMS.md) allowed by its section 7: copies and modified versions
must credit the original ("Based on LimeRSVP by Emil Haase" with a link to this repository) and
modified versions must be marked as different from the original.

LimeRSVP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.

## Credits

- [Atkinson Hyperlegible Next](https://github.com/googlefonts/atkinson-hyperlegible-next) by the
  Braille Institute, SIL Open Font License 1.1
- [jsoup](https://jsoup.org/), MIT License
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), a port of Apache PDFBox, Apache License 2.0
- Android Jetpack and Kotlin, Apache License 2.0
