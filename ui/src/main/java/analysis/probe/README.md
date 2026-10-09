# AMBE+2 hardware probe experiment

Compares JMBE's AMBE+2 synthesis with the AMBE-3000 chip by sending the chip crafted frames whose quantizer
indices we choose and analyzing what comes back. Each probe holds every index at a baseline and changes one thing
(a table row, the pitch, the voicing, a bit error pattern, a transition), so every chip-vs-JMBE difference in that
probe belongs to one cause.

The first version of the experiment fitted JMBE's decode tables (gain, PRBA24, PRBA58, HOC, rho) to the chip
(ProbeAnalyzer, ProbeTables, ProbeTiltAnalyzer, ProbeIndexSearch, ProbeTruthCheck and the codec's
AMBETableOverrides). Its results were the u3 bit-order fix and the conclusion that the published tables are right
and the differences lie in the chip's output response; those tools have since been removed, and the decoder uses
the published tables. The sections below that mention them are kept as a record.

## Running it

Every probe follows the same pattern:

```
# 1. Probe frames + manifest
java analysis.probe.ProbeGenerator --out resp --response        # or --pitch-sweep, --voicing, --noise, ...

# 2. Decode them on the chip (writes resp/chip.pcm)
java analysis.probe.ProbeChipRunner --dir resp

# 3. Compare with JMBE
java analysis.probe.ProbeResponseAnalyzer --dir resp            # or the probe's own analyzer
```

Dry run without hardware: `ProbeSimulator --dir resp` writes a fake `chip.pcm` decoded by JMBE with a known delay,
gain, rounding, noise and a different phase seed (`--shelf-db` adds an output filter, `--no-enhance` skips the
enhancement). The analyzer should recover the delay and level.

### Result: u3 bit order (decoder fix)

The tilt test on the hardware failed the tables-only check, but not along the tilt: the misfit followed the
parity of the PRBA24 row. In the full sweep, 235 of 238 even PRBA24 rows stayed unexplained after the fit against
39 of 237 odd rows. `ProbeIndexSearch --all-u3` then searched, for 84 probes, every value of the u3 bits after the
pitch/gain LSBs for the one that reproduces the chip's spectrum. The chip reads u3 as `b4[2:0] b3[0]`, where JMBE
(and, as far as checked, mbelib) read `b3[0] b4[2:0]`. `AMBEFrame` and `AmbeFrameEncoder` now use the chip's order.

Tilt run re-analyzed with the fixed order (`ProbeRelabel` re-labels the old manifest, same chip.pcm):

| | JMBE order | chip order |
|---|---|---|
| baselines, published tables | 2.2 to 4.3 dB | 0.8 to 1.7 dB |
| baselines, joint / separate | 2.83 | 1.08 |
| swept rows, published tables | 2.3 to 3.0 dB | 0.58 to 0.83 dB |
| swept rows, joint / separate | 1.90 | 1.02 |
| largest PRBA58 drift across tilts | 0.021 (t -24) | 0.0013 (t -1.7) |

So the published tables are close to the chip's, and the earlier table fits (and anything else tuned against the
chip before this fix) were fitting the bit-order error. Probe runs made before the fix were packed in the old
order: re-label them with `ProbeRelabel OLD_DIR NEW_DIR` before analyzing, or regenerate them.

### What is left after the bit-order fix: a high-frequency shelf

Re-analyzing the tilt run with the fixed bit order, chip minus JMBE on the steady baselines is no longer a table
pattern. One fixed curve explains it at both pitches and all five tilts (residual 1.06 -> 0.29 dB, about the phase
noise floor): flat within ~0.3 dB up to 2.4 kHz, then rising to about +3 dB at 3.5 kHz. JMBE's own synthesis is flat
(its audio matches its parameters within 0.0 dB at every frequency), and the enhancement fits (best strength ~1.0).
Because every pitch puts its top harmonic near 3.5..3.7 kHz, harmonic position and absolute frequency move together
there, so these frames cannot say whether the shelf is applied to the spectral amplitudes or to the output audio.

### Response probes (tones, pitch, level, voicing)

```
java analysis.probe.ProbeGenerator --out resp --response        # 3.5k frames, ~1.3 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir resp                  # also writes chip_dcmode.csv
java analysis.probe.ProbeResponseAnalyzer --dir resp            # results/response_summary.txt and CSVs
```

`--response --tones-only` writes only the tone group (1.4k frames, ~30 s), to rerun the tones without the rest.

- **Tones** (group 0): every single tone id 5..122 at AD 64, an AD sweep at 1 kHz, and the dual tones 128..163.
  If the tone response matches the voice shelf, the shelf is an output filter after synthesis; if tones are flat,
  it is in the spectral amplitude path. The AD sweep gives the chip's amplitude law (TIA-102.BABA-1: 0.711 dB per
  step; JMBE's ToneGenerator is linear in AD).
- **Pitch** (group 1): the flat baseline at every 4th b0, fitted as H(f) and as G(l/L).
- **Level** (group 2): offset and shelf from -50 to -10 dBFS (a compressor or level-dependent filter shows here).
- **Voicing** (groups 3, 4): every b1 at L 24 and L 40. Per band, the harmonic share of the band power decides
  voiced vs noise (threshold set per pitch from JMBE's own audio), then chip vs JMBE decisions and the noise level.

Tone frames follow TIA-102.BABA-1 Table 10, which repeats the tone index in u1, u2 and u3:
u0 = 63 then AD(6..1); u1 = ID(7..0) then ID(7..4); u2 = ID(3..0) then ID(7..1);
u3 = ID(0), ID(7..0), AD(0), 0000. The first response run used three guessed layouts without the u2/u3 copies,
and the chip answered INVALID DATA (frame repeat) for all of them. JMBE's AMBEFrame read AD(0) from u3 index 8,
which holds ID(0); it now reads index 9 (u3 bit 4). ProbeChipRunner now saves the chip's DCMODE per frame
(chip_dcmode.csv) and prints counts per group, and the analyzer reports them for the tone frames.

Dry run (`ProbeSimulator --dir resp --shelf-db 3`, which now also decodes group by group from
reset): delay 41 (true 37); the planted shelf comes back the same from tones and voice (0.02 dB apart) and at every
level; 117/117 single and 36/36 dual tones. The voicing classifier disagrees with an identical decoder on 2..3% of
bands (mostly L 40, where a frame spans few DFT bins per band), so treat chip disagreement at that level as noise.
H(f) and G(l/L) fit the pitch sweep equally well; the tones are what separate them.

Two analysis fixes that matter for real data: the PCM byte order is now taken as the one with less energy (the
smoothness test picked the wrong order on the tone-heavy plan, giving -5 dBFS noise), and the response analyzer
estimates the delay on the voice groups (a long run of equal-level tones gave the energy correlation nothing to lock
onto).

### Response results (chip, after the u3 fix)

- **The high-frequency shelf is in the voice path, not an output filter.** Tones come out flat within 0.0 dB from
  156 Hz to 3.8 kHz, while voiced harmonics show the shelf: flat to 2.4 kHz, then +0.6, +1.4, +2.0, +2.6, +3.1 dB
  at 2.5, 2.75, 3.0, 3.25, 3.5 kHz (relative to 0.5..2 kHz), -0.7 dB below 250 Hz. The level sweep shows the chip is
  linear: offset +1.2 dB and shelf +2.57 dB, both constant from -49 to -13 dBFS. The 2014 ICD enhancement constant
  (0.96 instead of 0.96 pi) does not explain it (residual 1.06 -> 1.42 dB).
- **Tone frames** (TIA-102.BABA-1 Table 10 layout): the chip flags every one TONE FRAME. Single tones are within
  0.65 Hz of id * 31.25 Hz. The amplitude law is logarithmic, 0.708 dB per AD step (spec 0.711), AD(0) at u3 bit 4.
  JMBE's ToneGenerator was linear (AD / 675): 24 dB loud at AD 64. Now 10^((-44.33 + 0.708 (AD - 64)) / 20) of full
  scale, which matches the chip within 0.1 dB over AD 24..120 (about 1 dB off at AD 4..16, near the chip's floor).
- **Dual tones**: the chip synthesizes each pair as two harmonics of one fundamental (exact integer ratios: 7/4,
  17/12, 11/7, 5/4, ...), so frequencies land up to ~20 Hz from the Table 9 values (e.g. KNOX 3: 1297 Hz instead of
  1279; busy tone 610/488 instead of 620/480). The fundamentals are not on the b0 pitch table. Each component is at
  the full single tone level. ToneGenerator now uses the measured pairs (synthesisFrequencies) with no halving; JMBE
  matches the chip within 0.3 dB on all 36.
- **Voicing**: most codes match. Differences: codes 1, 3, 11, 13, 15 have bands the chip renders partly voiced
  (harmonic share 0.4..0.7) where JMBE's table is fully voiced (JMBE's table has 0 = 1 = 3 and 10 = 13 = 15, which
  looks like a transcription problem); an isolated unvoiced band (codes 5, 6, 7) is noise only in its lower ~250 Hz
  in the chip; codes 24 and 25 at L 40 are 3..5 dB quieter with a periodic structure the other all-unvoiced codes do
  not have.
- **Delay estimate for tone-only plans**: the frame-energy correlation picked -320 on the tone run (the true delay
  is about +60: chip frame k holds input frame k's tone, switching ~0.2 frame late). Tone-only plans now take the
  delay that maximizes the tone fits over every frame of each tone, and the steady windows stop one frame early.

### Chip response in JMBE (AMBEChipResponse)

The shelf and a first-harmonic gain are now applied to the AMBE+2 enhanced amplitudes (AMBEModelParameters
.setSpectralAmplitudes, after the spec enhancement; AMBEChipResponse.setEnabled(false) restores the published
decode). Curves fitted in 125 Hz bins from the 30-pitch sweep and the tilt run together:

- shelf by harmonic frequency: 0 dB up to 2450 Hz, then 0.34, 0.72, 1.18, 1.58, 1.81, 2.05, 2.41, 2.85, 2.97, 3.21 dB
  at 2562..3688 Hz (125 Hz steps), extrapolated at ~2.6 dB/kHz above that. The knee sits on the voicing band 5 edge
  (2.5 kHz).
- first harmonic by f0 (superseded by the low-frequency model from the glide probe, below): this per-pitch table
  was the falling-pitch order of the sweeps measured as if it were static.

Validation on the chip runs (JMBE with the response vs the chip):

| | before | after |
|---|---|---|
| pitch sweep (30 pitches), residual after per-pitch offset | 1.16 dB | 0.29 dB |
| level sweep shelf (2.9..3.7 kHz minus 0.5..2 kHz), -49..-13 dBFS | +2.57 dB | +0.24 dB |
| tilt run baselines, residual | 1.06 dB | 0.31 dB |

An extra fitted curve no longer reduces the pitch residual (0.29 -> 0.29), so what is left is the phase-noise floor.
The noise/voiced balance in unvoiced bands is unchanged (-0.50 vs -0.46 dB; those estimates are +-1.5 dB per bin), so
the data neither requires nor rules out the shelf on noise; it is applied to all harmonics. Mid-band level is
unchanged; the chip remains ~0.5 dB louder overall.

### Full pitch sweep (first harmonic, f0 181 Hz question)

```
java analysis.probe.ProbeGenerator --out sweep --pitch-sweep      # 7.3k frames, ~2.8 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir sweep
java analysis.probe.ProbeResponseAnalyzer --dir sweep             # section B2, results/response_first_harmonic.csv
```

Every voice b0 (0..119) on a level-matched baseline, once per PRBA24 tilt row (236, 87, 266: falling, flat, rising),
each group led by a short level staircase so the delay estimate has energy steps to lock onto. Section B2 measures
the chip against the published decode (AMBEChipResponse off): l = 1..3 relative to each pitch's 0.5..2 kHz mean, and
flags pitches where l1 is more than 0.5 dB from the AMBEChipResponse table. Section B (against JMBE with the
response) is the residual after the correction. Dry run (`ProbeSimulator --dir sweep`, a fake
chip with the response): delay exact, l1 within 0.1 dB of the table at all 360 (pitch, tilt) cases.

On the 30-pitch voice run, b0 52 (f0 181 Hz) is not a missing l1 attenuation: l2 and l3 there read +0.8 dB (others
+0.0..0.2) and l1 sits ~0.9 dB below them, like its neighbours. Something lifts the low end at that pitch.

### Full pitch sweep results

- **L 20 block lengths.** The b0 51..53 anomaly (all L 20) moved l1..l3 together by an amount that follows the tilt
  (+1.35 dB falling, +0.8 flat, -0.75 rising): a different PRBA block split. ProbeBlockSearch tries every split near
  JMBE's for each L against the chip: L 20 fits 4/5/5/6 (0.34 dB, like L 19 and 21) where JMBE had 4/4/6/6
  (1.74 dB). Every other entry, L 9..56, is the chip's best fit (next best ~1 dB worse). Fixed in LMPRBlockLength.
- **First harmonic depends on history, not just f0.** After a pitch change the chip's l1 drops and recovers over
  tens of frames, with l1 at the right frequency from the second frame. With no pitch change (the level group, b0 63,
  from reset) l1 is 0 dB at every level. Resolved by the pitch-glide probe (below).
- **Shelf** confirmed over all 120 pitches and three tilts (pitch-sweep residual flat across frequency).
- **Pitch table.** The falling-tilt pitches that looked unclean were JMBE's pitch table. Fitting the chip's f0 at every
  b0: it follows 2^(8.643085 - 0.022042 b0) Hz within 0.04% (measurement precision); JMBE's table drifted up to +0.24%
  above that in b0 20..30, 66..76, 111..119 (now the law's values), and b0 17 has 12 harmonics in the chip, not 11.
  With the corrected table the harmonic model explains >= 99.6% of the chip's output at every pitch and tilt.

### Pitch-glide probe (first harmonic loss)

```
java analysis.probe.ProbeGenerator --out glide --glide          # 2.6k frames, ~1 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir glide
java analysis.probe.ProbeResponseAnalyzer --dir glide           # section E, results/response_glide.csv
```

Four groups from reset: pitch steps of 1, 2, 4, 8, 16 up and back from b0 64 (150 Hz) and b0 96 (92 Hz), each held
60 frames; glides over b0 50..80 (186..118 Hz) at 1 step per 1, 2 and 4 frames, each held 60 frames after; vibrato
around b0 70. Section E gives every frame's l1 - mean(l2, l3), chip minus the published decode.

What the chip does:

- **Falling pitch only.** A rise in pitch (b0 down) costs nothing from the next frame; a fall attenuates l1 by
  ~12 dB per octave of the fall (1 step -0.28, 2 -0.60, 4 -1.11, 8 -2.16, 16 -4.36 dB at 150 Hz), recovering
  exponentially with a ~20 frame (0.4 s) time constant. Glides behave as the same tracker lagging behind: the faster
  the fall the bigger the loss, and it decays after the glide ends.
- **Model:** a tracked pitch P = min(200 Hz, max(f0, f0 + 0.95 (P' - f0))), 160 Hz after reset; harmonics below P
  get (l f0 / P)^2. Fitted on 1990 steady frames: slope 12.1 dB/octave (a square law), decay 0.952/frame, reset
  ~160 Hz, fitted in both the log and linear domain (0.140 / 0.145 dB rms). The 200 Hz limit comes from the response
  run, where a falling-pitch series shows no l1 loss at all above ~200 Hz.
- **Plus a fixed low-frequency roll-off:** after a rise in pitch the 92 Hz group still reads -1.1 dB and steps below
  it settle lower still. A Butterworth high-pass magnitude at 75 Hz fits it best (order 3: 0.139 dB rms; order 2:
  0.230; order 1: 0.480).
- Both replace the first-harmonic table in AMBEChipResponse (applied to every harmonic, though in practice the
  tracker only reaches l = 1).

Validation, l1 relative to 0.5..2 kHz, chip minus JMBE:

| run | published decode | old table | tracker + roll-off |
|---|---|---|---|
| glide probe, 1976 frames | 1.364 dB | - | 0.145 dB |
| response run (pitch falls 4 steps per 20 frames), 30 pitches | 1.633 dB | 0.068 dB (fitted here) | 0.141 dB |
| full sweep (pitch falls 1 step per 20 frames), 359 cases | 1.417 dB | 0.673 dB | 0.472 dB |

What is left in the sweep is a constant +0.3 dB on the rising tilt (row 266) at every pitch and noise on the
falling tilt (row 236, weak l1) at low pitches, not a pitch-history effect.

### Voicing probe

```
java analysis.probe.ProbeGenerator --out voicing --voicing      # 4.8k frames, ~1.8 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir voicing
java analysis.probe.ProbeVoicingAnalyzer --dir voicing          # results/voicing_summary.txt, voicing_harmonics.csv
```

Every b1 code 0..31 held 16 frames on the level-matched flat baseline, at nine pitches (b0 20, 40, 63 = 295, 217,
153 Hz, and b0 84..114 = 110..72 Hz in ~8% steps), each group from reset after a level staircase. Together the low
pitches put a harmonic every ~15 Hz across the voicing band edges; the high pitches tell whether a transition
follows frequency or harmonic number.

ProbeVoicingAnalyzer classes every harmonic of every probe, chip and JMBE alike, from two measures over the steady
frames: the harmonic's share of its band power (sinusoid fitted at l*w0 over one frame) and the frame-to-frame
coefficient of variation of its fitted amplitude (a held voiced harmonic is steady, noise is Rayleigh with cv ~0.5).
Voiced: cv < 0.2 and share > 0.7; noise: cv > 0.33; otherwise mixed. Section F pools all pitches into 100 Hz bins
per code and prints JMBE's decisions, JMBE's audio through the same classifier (voiced harmonics carrying JMBE's
per-frame phase noise, in codes with many unvoiced bands, can read mixed), and the chip; ^ marks bins where the chip
and JMBE's audio disagree voiced vs noise. It also lists codes whose chip output repeats frame to frame and the noise
level relative to the voiced level per 500 Hz band. Dry run (`ProbeSimulator --dir voicing`):
no ^ bins, noise minus voiced 0.00 +- 0.02 dB in every band.

### Voicing results (chip)

- **Codebook.** Five entries differ from JMBE's table (which had 1 = 3 = 0 and 13 = 15 = 10, a transcription
  problem): 1 = 2 (band 7 unvoiced), 3 = 7 (band 5 unvoiced), 11 = 10 (bands 3..7 unvoiced; JMBE had band 7 voiced),
  13 = 12 (bands 2..7 unvoiced), 15 unvoiced throughout. 16..31 are unvoiced throughout. Fixed in VoicingDecision.
- **Band mapping.** A harmonic at f is voiced when the band holding f or the band holding f + 250 Hz is voiced: an
  unvoiced band between voiced ones is noise only from its lower edge to its middle (1000..1250 Hz for code 5 at
  every pitch from 72 to 295 Hz, so the shift is in Hz, not harmonics), while a voiced-to-unvoiced edge going up is
  on the band edge. With the chip's table, JMBE's mapping matches 98.7% of 7.8k harmonics, this rule 99.8% (the rest
  is classifier noise). In AMBEModelParameters.setVoicingDecisions, with AMBEChipResponse enabled.
- **Random phase on voiced harmonics.** MBE synthesis adds a random phase to voiced harmonics above L/4 in proportion
  to the share of unvoiced harmonics. The chip's voiced harmonics are far steadier in partly unvoiced frames (median
  frame-to-frame cv 0.063 vs JMBE's 0.236 at half unvoiced); scaling the random phase by 0.3 matches it at every
  share (0.3..0.6 unvoiced: chip 0.040 / 0.063 / 0.093, JMBE 0.044 / 0.067 / 0.093). MBESynthesizer
  .getPhaseNoiseScale, AMBE only, via AMBEChipResponse.
- **Noise level.** Relative to voiced harmonics the chip's noise is 1.6..1.8 dB lower below 1 kHz, ~1 dB lower at
  1..1.5 and 2..2.5 kHz, about equal at 1.5..2 kHz, 0.5 dB lower at 2.5..3.5 kHz and 3.8 dB lower at 3.5..4 kHz.
  AMBEChipResponse.noiseDb applies this to unvoiced harmonics; the residual is within +-0.2 dB per band (band 7
  -0.4).
- **Repeating noise.** For codes 24 and 25 the chip's output repeats exactly (24) or mostly (25) every frame at every
  pitch, with a period of 80 samples, 3..4 dB quieter than other noise codes; 20 and 28 repeat partly at some
  pitches. A quirk of the chip's noise generator for those codes; not modeled.

Result with all of the above, chip vs JMBE through the same classifier: no 100 Hz bin of any code disagrees voiced vs
noise (was 7 codes), noise minus voiced level -0.37..+0.18 dB per band (was -2.9..0).

### Unvoiced synthesis (from the voicing run)

The all-unvoiced codes of the voicing probe (16..23, 26, 27, 29..31 at nine pitches), chip vs JMBE audio:

- **Level vs pitch**: noise minus voiced within +-0.3 dB at every pitch from 70 to 295 Hz, so the published
  0.2046 / sqrt(w0) scaling matches the chip.
- **Within a harmonic band** the noise spectrum is flat in both (chip / JMBE ratio +-0.3 dB across the band at every
  pitch), so the per-band DFT scaling is the same.
- **Statistics**: kurtosis 2.6 (chip) vs 2.4 (JMBE), both near Gaussian.
- **Envelope within a frame**: the chip's noise power is flat across the frame (+-0.2 dB); JMBE's rose 0.7 dB in
  the middle, where the published weighted overlap-add divides two independent noise halves by a dipping sum of
  squared windows. Dividing by that sum to the power 0.85 instead of 1 makes it flat (+-0.1 dB)
  (MBESynthesizer.getUnvoicedNormalizationExponent, AMBE only).
- **Top of the band**: the chip's noise rolls off from ~3.55 kHz (-1 dB at 3.6, -4 at 3.65, -10 at 3.7, -21 at
  3.75 kHz) at every pitch, while tones and voiced harmonics are flat to 3.8 kHz: a low-pass on the noise only.
  Applied per DFT bin of the unvoiced synthesis (MBESynthesizer.getUnvoicedBinGain); chip vs JMBE shape now within
  ~1.5 dB to 3.75 kHz (the chip has a noise floor ~-30 dB above that).
- With both, the noise gain table (AMBEChipResponse.noiseDb) was refitted: noise minus voiced, chip vs JMBE, is
  now -0.12..+0.09 dB per 500 Hz band below 3.5 kHz and +0.5 dB above.

### Noise probe

```
java analysis.probe.ProbeGenerator --out noise --noise          # 1.3k frames, ~0.5 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir noise
java analysis.probe.ProbeNoiseAnalyzer --dir noise             # results/noise_summary.txt
```

What the voicing run could not show: G1 unvoiced vs voiced at nine levels (-50..-10 dBFS, is the noise linear?);
G2 three spectral tilts (does the noise follow the amplitudes per band?); G3 the power envelope around voiced to
unvoiced changes and back, and alternation every 1, 2, 4, 8 frames; G4 a pitch drop from 150 to 92 Hz made during
0, 10, 20 or 40 unvoiced frames, then voiced: the first harmonic loss after it tells whether the chip's pitch
tracker runs through unvoiced frames (as JMBE's does), holds, or forgets. Dry run (`ProbeSimulator --dir
noise`): G1/G2 within +-0.15 dB, G3 envelopes equal, G4 picks "runs through noise" (0.08 dB rms;
holds 2.36, forgets 1.68).

### Noise probe results (chip)

- **Pitch tracker holds through unvoiced frames.** After a 150 -> 92 Hz drop made during 10, 20 or 40 all-unvoiced
  frames, the first harmonic loss is the same as with no noise in between (-8.6 dB at the first voiced frame): a
  tracker that holds fits within 0.16 dB rms over 152 frames; one that runs through noise (JMBE until now) 2.25 dB,
  one that forgets 3.59 dB. AMBEModelParameters now updates the tracked pitch only on frames with a voiced harmonic.
- **The amplitude limiter treats noise like voice.** At the top gain (b2 31), where the published amplitude
  threshold (Alg #114-116) limits, JMBE's noise rose 4.4 dB relative to voiced (its unvoiced amplitudes carry the
  0.2046 / sqrt(w0) scaling, so they hit the limit less); the chip's ratio did not move. With the limiter measuring
  unvoiced harmonics without that coefficient (MBEModelParameters.getAmplitudeMeasureWeight), noise minus voiced is
  +0.0..+0.2 dB at every level from -50 to -10 dBFS.
- **Noise level depends on the code and on how much is unvoiced.** The all-unvoiced codes are not all equally loud
  in the chip (code 16 +0.5 dB, 23 -0.8 dB, the repeating 20/24/25/28 -1.9..-2.6 dB re their mean, each consistent
  over nine pitches to ~0.05 dB), and in partly voiced frames the chip's noise is louder the smaller the unvoiced
  share (+1.1..+1.8 dB below 0.3). AMBEChipResponse.noiseFrameDb: per code for 15..31, 1.6 dB * (1 - share) for the
  rest; the per-band table was refitted after it (voicing run residual -0.08..+0.04 dB per band below 3.5 kHz,
  +0.2 above; by unvoiced share within +-0.6 dB, was up to +1.8).
- **Tilt**: noise follows the amplitudes in all three tilts within about +-1.5 dB per band (one 12-frame segment each);
  no tilt dependence beyond that. The chip has more energy above ~3.8 kHz than JMBE's low-passed noise (a floor
  ~25..35 dB down), not modeled.
- **Transitions**: the chip switches voiced/unvoiced about 60 samples earlier relative to its level changes than JMBE
  does, with a brief 2.5..3.7 dB dip; JMBE's dip is shallower (1.6 dB) and wider. Not modeled.

### Bit error probe

```
java analysis.probe.ProbeGenerator --out errors --errors        # 1.4k frames, ~0.5 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir errors                # chip.pcm and chip_dcmode.csv (both needed)
java analysis.probe.ProbeErrorAnalyzer --dir errors             # results/errors_summary.txt, errors_frames.csv
```

Channel bit errors are flipped in the encoded frames (AmbeFrameEncoder.withErrors; errors.csv lists them), so the
chip and JMBE run their own FEC on the same bits. Clean frames are at pitch A (b0 63) and the test frames at pitch B
(b0 80): a test frame the decoder uses puts pitch B in the audio, a repeated one leaves only A. Four groups:

- H1: one frame with 0..5 errors in C0 (Golay 24) and 0..5 in C1 (Golay 23) between clean frames: which ones each
  decoder uses, repeats or mutes, and the chip's DCMODE for them.
- H2: runs of 1, 2, 3, 4, 5, 6, 8 and 12 frames with 4 errors in C0 (detectable, not correctable): repeats, when
  muting starts, and how the audio comes back afterwards.
- H3: 60 frames with a correctable load ((1,0), (1,1), (1,2), (1,3), (2,3), (3,2) errors per frame) on unchanged
  parameters: whether the error-rate average mutes or reshapes the audio.
- H4: runs of 1, 3, 6 erasure (b0 120) and silence (b0 124) frames with valid FEC.

Dry run (`ProbeSimulator --dir errors`): the B share separates used frames (0.36..0.77) from
repeated ones (0.11..0.14). It already shows two JMBE behaviours to check against the chip: four errors in the
Golay 24 word are not always detected (JMBE's decoder skips the overall parity check after correcting, so 4 errors
in the first 23 bits are "corrected" to a wrong word, and with C1 errors this can pass as a usable frame), and an
erasure frame is played as comfort noise at about the clean level rather than repeating the previous frame.

### Bit error results (chip)

The chip flags a frame either VOICE ACTIVE (0x0002) or INVALID DATA - FRAME REPEAT (0x0020); nothing else appeared.

- **Golay 24 (JMBE bug).** Four errors in C0 are always detected by the chip (all six 4-error frames flagged
  0x0020). JMBE's Golay24 decoder corrected the first 23 bits and never checked the overall parity bit, so four
  errors there were "corrected" to a wrong codeword (reported as 3); with C1 errors on top those frames were played
  with wrong parameters (-3.4 dB in the probe). Golay24.checkAndCorrect now checks the parity after correcting and
  returns 4 when it disagrees after 3 corrections. Five errors are miscorrected by both (as any decoder of this code
  would) and both use the frame.
- **Repeat rule.** The chip repeats only when C0 is uncorrectable: frames with 3 + 3, 3 + 4 and 3 + 5 errors are
  used, where the published rule (C0 >= 2 and total >= 6) repeats them. With the Golay fix and that rule
  (AMBEChipResponse.isInvalidFrame), JMBE's use/repeat decision matches the chip in all 36 single-frame cases.
- **Erasure and silence.** b0 120 (erasure) and b0 124 (silence) frames with valid FEC are INVALID to the chip and
  repeated like errored frames. JMBE played erasures as comfort noise at about the speech level and synthesized
  silence frames; both are now repeats.
- **What a repeat sounds like.** The previous frame again, identical below 2.5 kHz, with the voice-path shelf
  applied once more per repeat (+1.5 / +3.0 / +3.5 dB per repeat in the 2.5..3, 3..3.5, 3.5..4 kHz bands).
- **Muting.** Repeats 1..3, the 4th invalid frame fades out, from the 5th on true silence (-65 dB, no comfort
  noise) for as long as invalid frames continue (JMBE cycled mute, repeat, repeat, repeat with comfort noise). The
  first valid frame fades in from silence.
- **Prediction memory.** After invalid frames from C0 errors the next valid frames come back from a dip (-12 dB
  after one in this probe, then halving each frame); after erasure or silence frames, even a mute, there is no dip.
  The dip is the errored frame itself: the chip decodes its (uncorrected) bits into the gain and log2 amplitude
  memory while it plays the repeat, so the dip depends on what the errors decode to (none on the real NXDN calls,
  where a lone invalid frame left no dip). JMBE does the same (AMBEChipResponse.repeatMemory 4): the frames after
  each C0-error run are within 0.6 dB rms of the chip (max 2 dB), where clearing both memories (the earlier
  default, 3) was 2.4 dB rms and 13 dB max off, and keeping the previous memory 4.3 dB rms. (The earlier analysis
  picked 3 at a 73-sample delay that put the chip a frame early; aligned by level scatter the delay is ~0.)
- **Error rate.** See the error-rate results below: the chip's average is slower than the published one, which is
  why 2 + 3 errors muted only at frames 58-59 here (the slow average still held some of the earlier loads).
- **Timing.** At the level-envelope delay (73..75 samples here) the chip's dips and repeats appear about a frame
  earlier than JMBE's; at the delay that best matches whole-frame levels (~0 here, 6..20 samples on the real NXDN
  calls) they line up, except in the transition frame itself, where the chip's crossfade finishes ~40..80 samples
  sooner (the same lead seen in the voicing transitions). So the two delays differ by the chip's shorter or earlier
  transition, not by a frame; read H2 with --delay 0 to compare dip depths.

### Error-rate probe

```
java analysis.probe.ProbeGenerator --out rate --error-rate      # 3.2k frames, ~1.2 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir rate                  # chip.pcm and chip_dcmode.csv
java analysis.probe.ProbeErrorAnalyzer --dir rate               # section H5
```

Why did 3 + 2 errors per frame mute the chip at frame 43 (as the published average says) but 2 + 3 only at 58?
Fourteen 120-frame loads, each after 100 clean frames (so the average starts from ~0): 3+3, 3+2 and 2+3 at the
standard positions; 2+3 with the C1 errors all in data bits or all in parity bits, or the C0 errors in parity bits;
3+2 with the C0 errors in data bits, in parity bits, or including the overall parity bit (23); and 3+1, 1+3, 0+3,
3+0, 2+2, which the published average never takes past 0.096. For each, H5 gives the chip's first INVALID frame and
how many clean frames it stays INVALID afterwards, and the per-frame error count each of those implies for the
published average, next to JMBE's own count and onset (27 frames for 6 errors, 45 for 5). The error masks are now
read back with the plan (ProbePlan.read loads errors.csv).

### Error-rate results (chip)

The chip's INVALID onsets (frames into each load) and releases (clean frames after it): 3+3 72 / 29; 3+2 61 / 23;
every other 5-error load 63-64 / 22, whatever the bit positions (data or parity bits, either word, the C0 overall
parity bit); 3+1 107 / 3; 1+3 117 / 0; 0+3, 3+0, 2+2 never. The published average (0.95, 0.001064, 0.096) would
mute 5-error loads at 45 and release at once, never mute 4-error loads, and cannot give a later onset for 6 errors
than for 5 (the 3+3 load came first, from reset; the others start with what a slow average keeps through 100 clean
frames).

One leaky average with C0 and C1 errors counted alike fits all of it: rate = 0.99 rate + 0.01 (e0 + e1) / 47 (a bit
error rate over the 47 Golay-coded bits, ~100-frame time constant), invalid while rate > 0.0666 (no hysteresis).
It reproduces the chip's flag on 3202 of 3204 frames here and on all 1429 frames of the bit error run, including
that run's 2 + 3 (frames 58-59) and 3 + 2 (from frame 43) loads. AMBEModelParameters keeps this average
(getChipErrorRate) beside the published one, which still drives the adaptive smoothing; with AMBEChipResponse
enabled it decides muting (AMBEChipResponse.chipErrorRate, MUTE_ERROR_RATE).

### Timing probe

```
java analysis.probe.ProbeGenerator --out timing --timing        # 1.2k frames, ~0.5 min through the ThumbDV
java analysis.probe.ProbeChipRunner --dir timing
java analysis.probe.ProbeTimingAnalyzer --dir timing            # results/timing_summary.txt
```

Earlier runs suggested the chip puts some kinds of change at a different place in the frame than JMBE: measured
on the noise run, level steps line up with the chip delayed 16 samples, voiced/unvoiced switches with it 28..52
samples early, and on the glide run a pitch step 48 samples early. One I/O delay cannot give both, so the chip's
frame-to-frame interpolation differs. The timing probe (b0 63, at most -36 dBFS so +20 dB stays below the amplitude
limiter) repeats each event 8 times: level steps of +-20 and +-6 dB and one-frame level changes, a pitch step to b0
75 and back and a one-frame pitch change, a voiced/unvoiced step and back and one unvoiced frame.
ProbeTimingAnalyzer averages each event's response (32-sample power envelope in dB; pitch-B minus pitch-A harmonic
share over 96 samples; noise power left after a pitch-A fit over 64 samples) and finds the delay of the chip's
output that best lines it up with JMBE's (both decode the same parameter trajectory, so the multi-frame settling of
the predicted gain is common to both). Dry run (`ProbeSimulator --dir timing`, true delay 37):
every event type 34..40 (one 28), fit residual 1..6%.

### Timing results (chip)

Chip delay that lines each event type up with JMBE (8 events each): level steps and one-frame changes of 20 dB
+0..+10 samples; voiced -> unvoiced -52, unvoiced -> voiced -36, one unvoiced frame -44. The 6 dB level events and
the pitch events fit poorly (11..23% residual) and scatter (pitch -12, +48, +6), so they do not pin anything.

- **Voicing changes land ~45 samples (~6 ms) earlier, relative to level changes, than in JMBE**, consistent with the
  noise and glide runs. Delaying JMBE's voiced component against its noise does not reproduce it: it lines up the
  voiced-to-unvoiced onset and the level steps but pushes unvoiced-to-voiced 30..60 samples too early, because in
  the chip the noise ends and the voiced harmonics start together (both ~60 samples into the frame on the chip's
  clock). A faithful model would move JMBE's voicing crossfade windows (Alg #131/#132 and the noise overlap-add)
  ~40 samples earlier while leaving amplitude interpolation of continuing harmonics alone. Not done: a few
  milliseconds on voicing onsets, against a change to the synthesis windows that these measures cannot pin to
  better than +-15 samples.
- **The chip restarts voiced phases after unvoiced frames.** Averaging the 8 repetitions of each unvoiced-to-voiced
  change, the chip's voiced output after it is identical every time (the incoherent part is 60..90 dB down), so its
  harmonic phases start from a fixed state at a voicing onset; JMBE's continue from where they were (it advances them
  through unvoiced frames), so its onsets differ from repetition to repetition. Audible at most as a slightly
  different onset click; not modeled.

### Real calls

```
java analysis.probe.MbeChipRunner --dir CALLS [--recursive]      # NAME.chip.pcm, NAME.chip_dcmode.csv per NAME.mbe
java analysis.probe.MbeCompareAnalyzer --dir CALLS [--recursive] # mbe_compare_summary.txt, mbe_compare_frames.csv
```

MbeCompareAnalyzer synthesizes each call with JMBE (chip response on) and reports, per call and overall, the chip
delay, the JMBE - chip level per frame (median and spread, split by voicing), the difference per 500 Hz band, the
chip's invalid frames against JMBE's repeat / mute decisions with the level over the 8 frames after each, and the
worst frames.

First six NXDN calls (1280 frames, 745 above -50 dBFS):

- **Invalid frames.** The chip flagged 2 frames (0x0020), both C0 uncorrectable ([4, 3] errors); JMBE repeats the
  same 2 and no others. With the prediction memory cleared (the old repeatMemory 3) JMBE then came back 12..17 dB
  low for ~8 frames while the chip had no dip; decoding the errored frame into the memory (repeatMemory 4, now the
  default) leaves the frames after within ~1 dB, except the first (+2..+3 dB, the transition timing).
- **Level.** JMBE is 0.66 dB below the chip (spread 0.5 dB), the same in every call and in steady probe frames
  (-0.4..-0.6). It is spectral, not a gain: -0.8 / -0.6 dB in the 0..500 / 500..1000 Hz bands, within 0.3 dB from
  1 to 4 kHz, so a gain change would leave the upper bands high. Per harmonic on steady voiced frames the strongest
  harmonics are ~1 dB low and those 30 dB below the peak ~0.2 dB, which the enhancement weights (clamps, exponent)
  and the limiter (never engaged on these calls) do not change, since the energy is renormalized after them. Open.
- **Spectrum above 1 kHz** matches within 0.3 dB on voiced and mixed frames (without the chip response the top
  three bands are 0.6..2.1 dB low).

All 406 NXDN calls of the first collection (58.7k frames, 29.3k active):

- **Level** -0.64 dB (spread 0.6), bands -0.8 -0.5 -0.0 +0.2 +0.1 +0.1 +0.3 -0.1: the same as the first six.
- **Invalid frames.** All 95 chip 0x0020 frames are JMBE repeats (89) or mutes (6), and JMBE repeats no frame the
  chip plays. After them (75 runs) JMBE - chip is +2.1, +0.2, -0.2, -0.2, -0.1, -0.5, -0.2, -0.5 dB (frames +1..+8).
- **DCMODE 0x0000.** 283 frames (error-free, at call starts and ends) that JMBE repeated (84) or muted (199) come
  back from the chip as 0x0000 (COMFORT NOISE INSERTED) and near silent at once, where JMBE played two loud
  repeats first (+20..+46 dB over the chip): the radio's silence frame, see the silence probe.
- **Unvoiced frames** (2.2k) are +0.2 dB with a wide spread (1.4 dB), and hold all of the worst frames (+-6..9 dB);
  the analyzer now breaks the level down by b1 code.

DMR (400 calls, 27.2k active frames) and the NXDN calls again with comfort noise (406 calls):

- **Level** -0.53 dB DMR, -0.64 NXDN, same band shape (-0.7 / -0.6 dB below 1 kHz, within 0.3 above): not the
  radios' audio, the decoder.
- **Frame decisions.** Every chip 0x0020 frame is a JMBE repeat or mute (60 DMR, 95 NXDN), every 0x0000 frame
  JMBE comfort noise (6760 DMR, 283 NXDN, all the radio silence frame B9E881526173002A6B), every 0x8002 frame a
  JMBE tone (30, levels within 0.1 dB), except one DMR tone frame the chip flagged invalid: its u3 copy of the tone
  ID had a bit error (u3 is unprotected). The chip checks the ID copies; with the chip response on, JMBE now
  repeats a tone frame whose copies disagree (all 30 played tones and the tone probe's frames are consistent).
- **After invalid frames** +2.7 / +2.3 dB on the first frame (transition timing), then within 0.5 dB.
- **Unvoiced frames** (b1 15, 16; 3.3k) are +0.1 dB after an unvoiced frame, +0.9 after a partly voiced one and
  +2.4 after a voiced one: the voiced-to-unvoiced transition timing again, not the noise level.
- **Comfort noise level** is at the 16-bit floor (chip -5 dB re 1 LSB^2 on NXDN, -10 on DMR), so its 0..5 dB
  differences there are rounding.

### Level gap (held voiced frame probe, real calls)

JMBE's voiced speech was 0.55..0.66 dB below the chip on 806 real calls, -0.7..-0.8 dB below 500 Hz. A held-frame
probe (data/HOLD_voiced.mbe: 200 real voiced NXDN frames, each held 8 frames, 3.2k harmonics fitted) separates it:

- **Not a gain law.** The frame level gap is a near-constant -0.99 dB (spread 0.24) from 41 to 86 dB; the probes'
  level staircases show the same flat gap (-0.6) at every level. Scaling the decoder's log2-to-linear exponent,
  the enhancement clamps or exponent, the energy renormalization or the RM1 tilt term all fail: they redistribute
  energy within the frame (the renormalization holds it), or tilt the probes.
- **Contrast and frequency.** Per harmonic, the gap is -0.97..-0.8 dB on the frame's strongest harmonics and
  -0.35..-0.53 on those 20..30 dB down, in every frequency band (0.019 dB per dB of level below the frame peak),
  plus a frequency term (-1.1 dB at 0, -0.8 above 1 kHz).
- **Steady frames differ from speech.** That fit (frequency + contrast) brought the probe to 0 but left real speech
  +0.2..+0.6 dB too loud above 1 kHz: in running speech, where every frame changes, the chip is ~0.45 dB quieter
  relative to steady frames (frame transitions; see the timing notes). So the voiced gain is fitted on real speech:
  AMBEChipResponse.voicedDb, +0.75 dB at 250 Hz, +0.6 at 750, +0.1..0.15 from 1.25 to 2.75 kHz, 0 at 3.25, -0.25 at
  3.75 kHz, voiced harmonics only. On the six local NXDN calls: level -0.67 -> -0.08 dB, voiced bands -0.9..+0.4 ->
  -0.2..+0.3. Held frames: -0.99 -> -0.47 dB; probe staircases -0.6 -> -0.4.
- **Also found.** The first harmonic after a pitch drop: the probe needs less tracking loss than the glide-fitted
  tracker gives (half, or a ~150 Hz cap), but the glide probe fits the current tracker exactly; left as is. In
  partly voiced frames the 1.5..3.5 kHz bands stay +0.3..0.6 dB loud and 3.5..4 kHz -0.45 (not the noise level:
  -0.5 dB noise moved them 0.1).

### After the level-gap fix (806 calls)

Voiced frames -0.04 dB (DMR) and -0.12 (NXDN), every 500 Hz band within 0.2 dB; all active frames +0.01 / -0.07.
The remaining band errors are in the noise: steady unvoiced frames (b1 15..31 after the same code; 1454 frames)
were -0.46 +0.07 +0.20 +1.13 +0.18 +0.20 +0.33 -0.84 dB per band (JMBE - chip), the same 1.5..2 kHz excess in
isolated unvoiced bands of partly voiced frames (+0.97) and the same 3.5..4 kHz deficit (-1.09). AMBEChipResponse
NOISE now carries those corrections (1.5..2 kHz +0.6 -> -0.53 dB, 3.75 kHz -2.0 -> -1.16, 250 Hz -1.3 -> -0.84).
The noise probe's own tilt rows agreed at 1.5..2 kHz (-2.0, -1.2, -0.1 dB chip - JMBE).

Steady probes stay ~0.5 dB louder in the chip than running speech, voiced and unvoiced alike (noise probe: voiced
+0.47, unvoiced +0.75..1.0; held frames +0.47): the chip loses ~0.5 dB in running speech that JMBE does not, most
likely at frame transitions (the timing question).

### Transition probe (data/TRANS_real.mbe)

20 real voiced / unvoiced NXDN frame pairs: A = 8 voiced then 8 unvoiced frames, B = 2 + 2 three times, C = single
frames alternating, D = 4 frames of one voiced frame then 4 of another (plan in data/TRANS_real.plan.csv).

- **Steady frames:** chip louder than JMBE by 0.48 dB (voiced) and 1.12 dB (unvoiced, with the real-speech noise
  table). **Alternating:** B -0.17 dB, C +0.04 dB (JMBE - chip over the block): the more often frames change, the more
  the chip loses relative to its steady level, ~0.5..1 dB by single-frame alternation. That is why the level tables,
  fitted on running speech, sit ~0.5 dB below what steady frames need.
- **Timing:** the chip's voiced-to-unvoiced drop starts at +20 samples after the frame boundary and is ~-9 dB by
  +60..80; JMBE's starts at +60 and reaches that by +80..100. Voiced-to-voiced changes likewise start ~40..60
  samples earlier in the chip. JMBE's crossfade is the synthesis window overlap (samples 55..105 of the frame,
  centre 80); the chip's is centred ~40 samples earlier, and loses more energy per change.
- **A likely mechanism, not yet tested:** JMBE interpolates the amplitude and phase of harmonics below 8 when the
  pitch changes by less than 10% (no energy dip), and crossfades the rest with the window. If the chip crossfades
  (all or more) harmonics, a linear crossfade of unrelated phases loses ~1/3 of the energy over the overlap: about
  -0.5 dB per changed frame, the size of the gap.

### Pitch-step probe (data/PSTEP.mbe) and the voicing fade

One spectrum, only b0 changes: five base pitches (295..87 Hz), steps of 1.5%, 4.5% and 14%, held 8 frames each
way, alternating every frame and every 2 frames, and a single step (plan in data/PSTEP.plan.csv).

- **Pitch changes are not the loss.** The alternation level relative to the holds is within 0.1..0.3 dB between chip
  and JMBE at every step size, below and above JMBE's 10% interpolate/crossfade threshold. So the chip handles
  harmonics voiced in both frames like the published algorithm; a linear crossfade for them (tried) lost 0.3..1 dB
  too much here.
- **Voicing changes are.** In the transition probe the chip fades a harmonic whose voicing changes linearly from
  the frame boundary over ~110 samples; the published window fades over samples 55..105. With that fade
  (AMBEChipResponse.VOICING_FADE_SAMPLES, Alg #131/#132 only, not into or out of comfort noise, where the silence
  probe shows the published timing): transition profile error 1.65 -> 0.88 dB rms, single-frame voiced/unvoiced
  alternation relative to steady +0.52 -> +0.11 dB, 2-frame +0.31 -> +0.15. On the six local calls, unvoiced frames
  after partly voiced ones +1.68 -> -0.16 dB, partly voiced after unvoiced -0.40 -> +0.15; error probe recovery
  unchanged (0.59 dB rms). Voiced-to-voiced spectral changes (block D) still lose ~0.2 dB more in the chip.
- **Whole-frame switches only.** On 806 real calls the fade brought unvoiced frames to +0.03 (DMR) / -0.01 (NXDN) dB
  (from +0.37 / +0.25, spread 1.6 -> 1.2 / 1.5 -> 1.3; code 16 -0.1), but applied to band-wise voicing changes it
  left the unvoiced bands of partly voiced codes 8..14 0.4..1.0 dB low (they were within +-0.5). So it applies only
  when one of the two frames has no voiced harmonic (all of the transition probe's switches).

### Silence probe

The second 406-call run identified the DCMODE 0x0000 frames: the radio's silence frame, b0..b8 = 124 16 1 52 79 18
14 12 1 (B9E881526173002A6B, 189 + 80 of the 283, the rest differing only in b4..b8). The chip plays the first one
like the frame before (fading out) and is silent from the second, flagged 0x0000; JMBE repeats twice more first.
The bit error probe's b0 124 frames (b1..b8 of a voice frame) were 0x0020 repeats instead, and b0 125 and 126
frames in the calls were 0x0020 too, so the chip looks at more than b0. SilenceProbe changes one field of the
radio's frame at a time to find which:

```
java analysis.probe.SilenceProbe --out silence       # 19 call files (.mbe), one variant each
java analysis.probe.MbeChipRunner --dir silence      # NAME.chip.pcm, NAME.chip_dcmode.csv per file
```

Results (chip, 19 files):

- **Comfort noise, not silence.** DCMODE 0x0000 is COMFORT NOISE INSERTED. b0 124 or 125 with b1 = 16 (all
  bands unvoiced) is comfort noise; b1 0, 15 or 24 make the same frame invalid (0x0020, repeated), as do b0 120
  and 126 with the radio's other fields. b0 63 with the radio's fields is ordinary (unvoiced) speech.
- **Level from b2.** 15, 25, 34, 40, 46 dB for b2 1, 4, 8, 16, 25: 6.02 x the gain table step plus a constant
  (+-1 dB), steady through the run. The radio frame's b2 1 puts it ~45 dB below the speech before it.
- **Shape from b3..b8.** b3 87 instead of 52 flattens the spectrum; b4..b8 change it a little.
- **Prediction memory frozen.** The noise is predicted from the frame before the run, which stays the memory:
  steady level, and speech resumes at full level on the next frame (51, 60 dB, where decoding through the noise
  frames gave 27, 42). At a call start (reset memory) the noise is near silent (-8..-19 dB) and the speech after
  it ramps up as from reset.

JMBE now does the same (AMBEChipResponse.isComfortNoiseFrame, COMFORT_NOISE_DB): the comfort-noise frames are
decoded as unvoiced speech against the frozen memory and played 4.4 dB down; level spread over the variants 1.5
dB, speech after them and at call starts within 1..2 dB. Still off: the chip's noise has 3..8 dB less at 1..2.5
kHz and ~5 dB more at 3..3.5 kHz than JMBE's decode (no f0 / L for b0 124 fits better), and the 4th invalid frame
of a run fades in the chip (57 dB) where JMBE mutes.

### Tables or enhancement? (tilt test)

A table error adds the same thing to the log amplitudes wherever the row is used, so one value per row explains
the chip whatever else is in the spectrum. A difference in a spectrum-dependent step (the enhancement weights
depend on the frame's own spectral moments and level) makes the value a row "needs" change with the spectrum.
The tilt test sweeps the same PRBA58/HOC1/HOC2 rows on top of baselines with different PRBA24 slopes:

```
java analysis.probe.ProbeGenerator --out tilt --tilt-test        # L 17 and 40, 5 tilts, 7.8k frames, ~3 min
java analysis.probe.ProbeChipRunner --dir tilt
java analysis.probe.ProbeTiltAnalyzer --dir tilt                 # (removed) results/tilt_summary.txt, ...
```

Dry run controls (the simulator then also planted table errors, vs. `--no-enhance`, a fake chip that skips the
enhancement):

| | table-only fake chip | no-enhancement fake chip |
|---|---|---|
| 1. baselines, joint / separate RMS | 1.02 | 1.21 |
| 2. swept rows, separate-fit RMS per tilt | 0.28 to 0.57 dB | 0.86 to 1.59 dB, worst at the steepest tilt |
| 2. swept rows, joint / separate RMS | 1.01 | 1.04 (not a sensitive measure) |
| 3. PRBA58 change across the tilts | at most 0.0006 (t 2.4) | 0.005 to 0.019 (t 7 to 19) |

So read the baseline ratio, whether a free per-tilt fit still leaves a residual that grows with tilt, and the
trend sizes (against the table corrections, about 0.05 RMS for PRBA58 on the hardware).

## The probe schedule (ProbePlan)

There are three pitch groups by default: L = 56, 40, 24 (b0 118, 96, 63). All bands are voiced (b1 = 0).
The baseline uses the smallest-norm row of every shape table, so the spectrum is near flat and the
enhancement weights stay off their clamps. The baseline b2 is chosen so the steady level is about −30 dBFS.

| Probe | Frames | What it measures |
|---|---|---|
| BASE | 24 (start), 16 (between tables) | reference level/shape, repeatability |
| GAIN k | 1 frame at b2 = k, then 8 baseline | gain entry k relative to the baseline entry; gain memory |
| PRBA24/PRBA58/HOC1–4 k | row k held 16 frames | first frames: step response (rho); last 8: steady state, where log2 amplitude = (T − mean T)/(1 − rho) + gain |

Group 0 sweeps every PRBA24/PRBA58 row. The other groups sweep every 8th row, plus all gain and HOC
rows. The groups' different block lengths separate PRBA from HOC, and agreement between groups is the
consistency check (`between-group spread`).

## The table fit (removed)

ProbeAnalyzer estimated the delay, pitch scale, level, rho and gain memory from step responses, then fitted every
table row with JMBE as the forward model and wrote the result as table overrides. On a simulated chip with planted
table errors it recovered PRBA58 and HOC1 to within 0.003..0.005 RMS. On the hardware, once the u3 bit order was
fixed, the published tables explained the chip, so the fit and the override mechanism were removed.

## Limits and things found along the way

- **Not yet run on the hardware.** The Gradle build could not run in the session that wrote this (no
  network access to the Gradle/Maven repositories). Everything was compiled with `javac` against stub
  logging/FFT jars and validated with `ProbeSimulator`. (Since run on the hardware; see the results above.) `ProbeChipRunner` is untested against a real
  ThumbDV: it uses `ThumbDv.decode` the same way `MBEViewer` does, and retries a group whose response
  count comes back short.

- **Low gain entries are not measurable this way.** A single frame far below its neighbors is mostly
  filled in by the synthesis crossfade, so the probe's span power barely depends on it. Holding the low
  entry instead would put the level 40–80 dB down, below the chip's 16-bit floor. These entries are
  reported and left at their published values. They are near-silence frames, so they matter little for
  audio.
- **Steep PRBA24 rows** (strong tilt) leave few harmonics above the floor, and some still differ by more
  than 1.5 dB after the fit. Fits that move a row without explaining the chip better are not used. If
  the real chip shows many such rows, the enhancement (not the tables) is the likely difference.
- **Frame byte layout.** The hex frames hard-coded in `ThumbDv.main` decode in JMBE with 2–3 Golay errors
  each, so they are not in JMBE's layout. The NXDN reference pipeline sends `VoiceFrame` bytes, which are
  in JMBE's layout, and that is what `AmbeFrameEncoder` produces (round-trip verified on 20,000 random
  index sets). The pitch/explained-energy check in step 2 catches a mismatch immediately.
- **JMBE synthesis is not repeatable.** `MBESynthesizer.reset()` randomizes the initial phases. Separately,
  `MBEModelParameters.getUnvoicedBandCount()` counts the unused index 0, so fully voiced frames still get
  random phase jitter on the upper harmonics. That looks like an off-by-one; Luv should count l = 1..L.
  Together these move per-harmonic amplitudes by up to ~1 dB between runs. `setRandomSeed` was added so
  the analysis is deterministic. The off-by-one itself is unchanged.
- **rho.** `AMBEModelParameters.RHO` was 0.69 at the start, while `AmbeDecodeRegress` assumes 0.65. The chip's
  measured value, 0.642, is now the codec's RHO.

## Codec changes (default behavior unchanged)

- `MBESynthesizer.setRandomSeed(long)`.
