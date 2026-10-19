# LimeRSVP design notes

How the app is built, why it behaves the way it does, and what was tried and removed. Read this
before changing anything a person sees, touches or hears.

## Principles

- Every touch gets an answer at once: it acts, answers softly (a breath, a spin), or shakes its head.
- Motion follows something real and never jumps; nothing changes size when data arrives.
- Unknown stays unknown ("—"); estimates wear a "~".
- Vibration is rare and marks one moment; the app is silent.
- Few, plain words. No hint icons: the gestures teach themselves, and the tutorial teaches by doing.
- Designed for the hard case first: darkness, the largest font, weak phones, long languages.

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:lintDebug
```

AGP 9.4 (built-in Kotlin), Gradle 9.8, Compose BOM 2026.09. Libraries: jsoup (EPUB/HTML),
PDFBox-Android (PDF text). Everything else is hand-written.


## Architecture (`com.limersvp`)

```
core/
  BookContent     The whole book flattened: one String + token start/end IntArrays + flag
                  bytes + chapters. Saved deflated (varints). FORMAT bump = re-parse caches.
  BookBuilder     Paragraphs of any language -> tokens. Spaced scripts split on spaces with
                  lone punctuation glued to a neighbour; unspaced scripts (CJK, Thai, Khmer...)
                  via ICU dictionary word breaks; Japanese particles join the word before.
  Text            Scripts, punctuation (sentence/clause, abbreviations), cleanup (soft hyphen,
                  zero-width, ligatures), ORP by grapheme cluster, long-word splitting.
  Timing          Word "units": length, digits, comma/sentence/paragraph pauses. Prefix sums
                  give exact time left at the current speed.
  RsvpEngine      Frame-driven clock (withFrameNanos), carries overshoot into the next word,
                  ramp-up on press, rewind to sentence start after a break, heading cards.
parse/            EpubParser (spine, nav/NCX TOC, landmarks, cover), HtmlWalker (what never
                  gets read: ruby, footnotes, noterefs, page-number markers, asides, hidden),
                  PdfParser (lines with font size -> headings, headers/footers/page numbers
                  removed, line-end hyphens joined, small-print footnotes dropped, outline ->
                  chapters placed on the matching heading), TextParsers (TXT, HTML),
                  Heuristics (chapter words + front matter in many languages), BookImporter.
data/             Settings (SharedPreferences, read synchronously so the first frame is right),
                  Library (books.json), Stats (per day + sessions; real wpm = words/active time).
feel/             Haptics (per-motor vibration), Motion (shake, breathe, grace, reduce motion).
ui/               AppRoot (tabs stay alive, floating pill nav), reader/ (ReaderScreen with all
                  gestures, WordStage, BookView, Panels), library/, stats/, settings/,
                  components/ (SpeedKnob, ProgressRail, Choices, Toggle, HoldButton, GrowPanel).
```

## Reader gestures

- One finger held anywhere: read. Starts at 40% speed and eases to full over the ramp
  (shorter after a short lift). Lifting stops on the same frame.
- A quick tap (under 0.2 s, nothing read yet): back to the start of the sentence, or of the
  previous one when already there. One tick. (Tap mode keeps tap = start/stop.)
- Two fingers sideways: scrub like a cursor (right = forward, mirrored for RTL books). The page
  opens and follows the word; far out and held, it keeps travelling. One tick when the axis
  locks, a tick per chapter crossed, a wall at the ends.
- Two fingers up/down: speed in 25 wpm steps; the knob turns and lifts, the bubble shows it.
- A second finger arriving before any word was read cancels the first finger's press entirely
  (`RsvpEngine.cancelPress`), so a two-finger gesture never moves the position by itself.
- Knob: 100-1200 wpm, 600 straight up. Turn it; detent tick every 25, own click at 600, rubber
  band + one click at the ends. Tap = one gentle spin (it is a fact). Long press = home to 600.
  Touch area is 24 dp larger than the drawing.
- Progress line: drag to travel (lands on a paragraph start, "Back to 34%" chip appears).
  A tap only breathes: jumping by tap is too easy to do by accident.
- Volume keys: speed (default), hold-to-read, or left to the system.

## Tutorial

Shown the first time any book is opened, at the top of the reader where it never covers the word.
It teaches by doing: each line waits until the person has actually done the gesture (read a few
words, tapped back, scrubbed, changed the speed), then moves on with one click. It dims while
reading, can be skipped, and Settings > "Show the tutorial" replays it on the sample story.

## Release (F-Droid)

- License GPL-3.0-or-later plus section 7(b)/(c) terms (`ADDITIONAL_TERMS.md`): forks keep
  "Based on LimeRSVP by Emil Haase" with the repository link, and are marked as modified.
- Settings > About shows the copyright, the no-warranty line, the source link and every
  component's license text (`assets/licenses`), as GPLv3 asks of an interactive program.
- Release builds: R8 with keep rules for PDFBox-Android and jsoup (they load resources next to
  their classes), no Google dependency-info block, no VCS info (reproducible), pinned Gradle
  wrapper checksum. No internet permission and no proprietary libraries.
- Store texts, icon and screenshots live in `fastlane/metadata/android/` (en-US, cs-CZ).

## Decisions (2026-10-19)

- Knob scale is asymmetric (500 wpm left of home, 600 right) so 600 can sit at the top.
- "Back to the sentence start after a break" is applied when the book opens (the word shown is
  the word reading starts from) and on a press after more than 30 s, never after a jump the
  person chose. 12 s rewound people who had only looked away, and it felt like the text was
  repeating itself.
- Headings are shown as one card (consecutive heading paragraphs merged, first line dim and
  uppercase), not word by word.
- The sentence under the word is shown only when stopped, only for words (not heading cards),
  with its height reserved (3 lines) so nothing moves.
- Night red recolours everything including covers; red and amber themes keep their own
  highlight. Red fades in over 900 ms, out over 420 ms.
- Detent ticks play only on motors that can tick (primitives or amplitude control); cheap
  buzzers get only the moments (axis lock, home, walls, chapter, finished).
- Vibration talks to the Vibrator directly (not performHapticFeedback), because the system
  touch-feedback switch and OEM skins silence the latter. On Android 9 and on motors without
  amplitude control, pulses are long enough (22-45 ms) to actually be felt.
- Unknown stays "—" (time left while computing, real speed under 30 s of reading).
- Finishing any book quietly turns the knob's dot gold for good. Seven quick taps on the
  version line in Settings open a hidden story. Neither is announced anywhere.
- Texts live in `res/values/strings.xml` and `values-cs`; units and numbers go through
  `ui/format/Fmt` (`rememberFmt()`), never `+ " min"` in code. Czech: the polite "vy" form only
  where a sentence must address the reader, "sl/min" for wpm ("slov/min" does not fit on the
  knob), "zbývá ~12 min" for "left", "34 %" with a non-breaking space. The sample story and the
  hidden story have Czech copies in `res/raw-cs`: change both languages together.

## Reading research behind the timing (2026-10-19)

- Going back is how readers repair what they missed; RSVP removes it and comprehension drops
  (Schotter, Tran & Rayner 2014, Psychological Science). Hence: tap = back one sentence, the
  sentence shown when stopped, two-finger scrub, rewind after a break.
- Taking away the sentence-end pause hurts RSVP comprehension; clause/punctuation pauses are what
  readers prefer. Units (`core/Timing`): sentence +1.1, ellipsis +1.5, ; : +0.6, comma/dash/")"
  +0.45, line break 0.6, paragraph 1.6, heading card 2.2 + 0.45 per word.
- Readers dwell longer on long, rare and unfamiliar words and skim short function words
  (Rayner's eye-movement work). Function words (<= 4 letters, en/cs/de/fr/es/it/pt list) 0.8,
  words over 5 letters +0.055 per letter (max +0.66), names mid-sentence +0.3 (not for German,
  which capitalises every noun), hyphenated +0.3, digits +0.25, first word of a sentence +0.1.
  Net effect: nominal 400 wpm reads at about 380 real words/min on prose.
- RSVP cuts blinking and tires the eyes (Benedetto et al. 2015): after 20 s without a paragraph
  break, the next sentence end holds 380 ms longer (`RsvpEngine.REST_*`).
- Comprehension falls once speed passes normal reading (roughly 250-300 wpm; Ricciardi &
  Di Nocera 2017). 600 stays at the top of the knob by design; the knob reaches 100 for careful reading.
- ORP index by length 0/1/1/1/1/2/2/2/2/3/3/3/3/4 (left of centre, O'Regan's optimal viewing
  position), placed at 42% of the width with real font metrics; words over 13 letters (the
  span readers take in) are split.

## Readability and characters (2026-10-19)

- Default word font is Atkinson Hyperlegible Next (Braille Institute, OFL, `res/font/atkinson_next.ttf`,
  license in `assets/licenses/`): letters built to never be confused (I/l/1, O/0, b/d). Serif vs
  sans makes no measurable speed difference (Arditi & Cho 2005); letter distinctiveness and
  x-height do. It covers all Latin incl. every Czech letter; a word with Cyrillic, Greek or Asian
  letters is drawn whole in the system font (`coveredByClear`), never as two typefaces.
- Variable weight: 380 on dark themes, 420 on Day, 620 for Bold. Light text on black looks
  heavier and blooms (halation), so the dark themes take a little weight off. Text stays
  off-white, never pure white.
- Letter spacing stays normal: RSVP reading is fastest at standard spacing, wider spacing slows
  it (Chung 2002; Yu, Cheung, Legge & Chung 2007).
- A too-wide word shrinks to at most 60%, never below: under the critical print size reading
  speed drops sharply.
- Czech/German closing quotes “ ‘ « count as closers (so `domů.“` ends a sentence); Czech
  abbreviations (např., tj., tzv., Ing., str. ...) and dates ("21. října") do not end sentences.
- Long words split at syllable-like points (V|CV, then VC|CV), never inside a grapheme (ř, é)
  or a digraph (Czech "ch", German "sch", ck, th, dž...).
- Latin-script books without a language tag get one guessed from telltale letters and common
  words (cs, sk, pl, de, fr, es, it, pt, en).

## Removed (and why) - 2026-10-19

- The "Narrow" (condensed) word font: narrow letters are harder to tell apart, the opposite of
  what reading research asks for. "Clear" (Atkinson) took its place.

- The gesture-guide sample text: it explained the app instead of being something to read, and
  repetitive stress-test texts looked like the reader was stuck. The sample is now a real story.

- Letter spacing on the heading card's first line: an Android span quirk wrapped a line that fit.
- Chapter dots on the progress line when chapters are closer than 10 dp: it read as a dotted line.
- A see-through scrim behind the scrubbing page: the RSVP word showed through like a glitch.
- Sounds: a reading app is silent.
- Tap-to-jump on the progress line (see above).
