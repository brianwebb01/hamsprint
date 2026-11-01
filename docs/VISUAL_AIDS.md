# Visual aids

Training cards can carry a visual aid: a small native diagram shown on the
first look at a new question and again after a wrong answer. Practice exams
never show aids.

Aids live in `app/src/main/assets/aids/{technician,general,extra}.json`, one
JSON array per pool. Each entry targets one question:

```json
{ "q": "G1A03", "kind": "bandplan", "band": "20m", "mark": [14.025, 14.15], "caption": "General CW and data start 25 kHz above the band edge." }
```

Rules for every entry:

- `q` is a question id from that pool. At most one aid per question.
- `kind` is one of the kinds below; other keys are that kind's parameters.
- `caption` is optional, at most 90 characters, one short sentence that is a
  memory hook for the **correct** answer. Plain ASCII punctuation only: never
  an em dash or en dash. Use a comma, colon, or period instead.
- Facts that must be right (band edges, bandwidths, code meanings, formulas)
  live in the app's code. Parameters only *select* what to show or supply the
  numbers already printed in the question. The app computes results itself.
- For "which symbol is X" questions, use `component` only when the drawn symbol is exactly X, not a close relative (a Schottky drawn as a plain diode is wrong).
- Only add an aid when it makes the correct answer easier to recall. Skip
  questions where no picture helps; that is expected and fine.
- Electrical values are plain numbers in base SI units: ohms, volts, amps,
  watts, farads, henries, hertz (so 10 microfarads is `1e-5`). Radio
  frequencies in `bandplan`, `spectrum`, `wavelength`, `antenna` are MHz.
- Displayed frequencies follow the question. The pools write operating
  frequencies in MHz from 1 MHz up and in kHz below it, and the aids do the
  same by default. When a question writes them the other way (G1A09 lists
  "21300 kHz"), add `"unit": "kHz"` to its `bandplan` or `spectrum` aid and
  write the caption in kHz too. Data values stay in MHz either way.
  Bandwidths and offsets (3 kHz, 600 kHz) are spans, not frequencies, and
  are always fine in kHz or Hz.

Run `python3 tools/aids/validate.py` after editing. The unit test
`VisualAidAssetsTest` enforces the same rules and checks every computed
result against the question's correct answer.

## Kinds

### Bands and operating

| kind | parameters | shows |
|---|---|---|
| `bandplan` | `band`, optional `mark` (MHz number, or `[lo, hi]` range), optional `unit` (`kHz` or `MHz`) | US privileges on one band for Extra, General and Technician, color coded by mode (CW only, CW and data, phone and image). `mark` highlights a frequency or segment. |
| `spectrum` | optional `bands` (list), optional `mark` (MHz), optional `unit` for the mark | All amateur bands on one log scale with HF, VHF and UHF zones; highlighted bands pop. |
| `wavelength` | `freqMHz` **or** `meters`, optional `example`: true | `wavelength (m) = 300 / f (MHz)` worked for that value, with the matching band. Set `example` when the question asks for the formula and the value is only an illustration; it is then labelled "Example" and not checked against the answer. |
| `repeater` | `band`: `6m` `2m` `1.25m` `70cm`, optional `tone`: true | Input and output frequencies and the standard offset for that band; `tone` adds the CTCSS sub-audible tone. |
| `modes` | `highlight`: `FT8` `PSK31` `CW` `RTTY` `SSB` `AM` `FM` `ATV` | Signal bandwidths compared on one log scale. |
| `ionosphere` | `layer`: `D` `E` `F1` `F2` `F`, `time`: `day` or `night` | Layer heights over a curved Earth with a skip path; the chosen layer is emphasized. |
| `antenna` | `type`: `dipole` `vertical` `yagi` `loop` (a small receiving loop, well under one wavelength around), optional `freqMHz`, optional `highlight`: `reflector` `driven` `director` `radials` | Antenna sketch. With `freqMHz`, dipole length `468 / f` ft or quarter wave vertical `234 / f` ft is computed. |
| `callsign` | `call` (e.g. `KD9XYZ`) | Prefix, region digit and suffix colored apart, with the `NxM` format. |
| `phonetic` | `text` | The text spelled in the ITU phonetic alphabet. |
| `codes` | `set`: `q` `prosign` `rst`, `highlight` | A grid of codes and meanings with one highlighted. Q: QRM QRN QSY QSL QRP QRO QTH QRZ QRL QRS QRQ QRT QRV QSO QSB QRX. Prosign: CQ DE K KN SK AR BK 73. RST: R S T. |

Bands: `2200m` `630m` `160m` `80m` `60m` `40m` `30m` `20m` `17m` `15m` `12m`
`10m` `6m` `2m` `1.25m` `70cm` `33cm` `23cm` `13cm`.

### Electronics (computed)

Give exactly the inputs the question states; the app solves for the rest.
For `ohms`, `power` and `reactance`, give only `solve` (no values) when the
question asks for the formula itself; the formula is then shown highlighted.

| kind | parameters | shows |
|---|---|---|
| `ohms` | `solve`: `E` `I` `R`, plus the other two of `E` `I` `R` | Ohm's law triangle with the unknown covered and the worked result. |
| `power` | `solve`: `P` `E` `I` `R`, plus any two others of `P` `E` `I` `R` | Power formulas (`P = E x I`, `I^2 x R`, `E^2 / R`) worked. |
| `combine` | `part`: `resistor` `capacitor` `inductor`, `arrangement`: `series` `parallel`, `values` (list) | The parts drawn in series or parallel with the total computed. |
| `decibel` | `db` **or** `ratio` (negative dB and ratios below 1 are losses), optional `watts` (starting power) | dB ladder (3 dB is 2x, 10 dB is 10x) with the step highlighted; with `watts`, the resulting power. |
| `prefix` | `value`, `from`, `to` (each one of `p` `n` `u` `m` `` `k` `M` `G`) | Metric prefix ladder with the decimal shift for that conversion. |
| `waveform` | `given`: `peak` `rms` `pp`, `value`, `solve`: `peak` `rms` `pp` `pep`, optional `ohms` (needed for `pep`) | Sine wave with peak, peak to peak and RMS marked; result computed. |
| `swr` | `z1` and `z2` (ohms) **or** `swr` | SWR gauge; with impedances, `SWR = larger / smaller`. |
| `reactance` | `solve`: `XL` `XC` `f0`, plus `f`, `L`, `C` as needed | Reactance or resonant frequency worked. |
| `timeconstant` | optional `R` and `C`, optional `tau` (1 to 5), `show`: `charge` or `discharge` | RC curve with 63.2% per time constant; with `R` and `C`, `tau = R x C`. |
| `transformer` | `np`, `ns`, optional `vp` | Turns ratio and secondary voltage. |

### Components and circuits

| kind | parameters | shows |
|---|---|---|
| `component` | `symbol`: `resistor` `potentiometer` `capacitor` `variable_capacitor` `inductor` `transformer` `diode` `zener` `led` `npn` `pnp` `jfet` `mosfet` `battery` `fuse` `switch` `relay` `lamp` `antenna` `ground` `crystal` `speaker` | The schematic symbol with its name and unit. |
| `meter` | `meter`: `voltmeter` `ammeter` | Where the meter goes in a circuit: across (parallel) or in line (series). |
| `filter` | `type`: `lowpass` `highpass` `bandpass` `notch` | Frequency response curve with the pass band shaded. |
| `logic` | `gate`: `AND` `OR` `NAND` `NOR` `XOR` `XNOR` `NOT` | Gate symbol and truth table. |

### Key number

| kind | parameters | shows |
|---|---|---|
| `fact` | `value`, optional `unit`, `label` | One large number for a rule or limit (e.g. `"value": "10", "unit": "minutes", "label": "Identify at least every"`). `value` must appear verbatim in the correct answer. Use only when the answer is a number. |
