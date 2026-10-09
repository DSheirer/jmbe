/*
 * ******************************************************************************
 * Copyright (C) 2015-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * *****************************************************************************
 */

package analysis.probe;
import jmbe.codec.ambe.AMBEModelParameters; import jmbe.codec.ambe.ambePlus2.LMPRBlockLength;
import java.nio.file.*; import java.util.*;
/**
 * Which PRBA block lengths (LMPRBlockLength) make JMBE's spectral shape match the chip's, per harmonic count L? For
 * each L, tries the splits within +-2 of JMBE's entry (each block 1..17) and scores chip - JMBE enhanced amplitude
 * (mean removed per probe, steady frames 10..18) over every PITCH probe with that L. Input is a pitch sweep directory
 * (frames.hex, manifest.csv) and the analyzer's results/response_pitch.csv for that run (chip dB per harmonic).
 * Found that the chip splits L 20 as 4/5/5/6 where JMBE had 4/4/6/6; every other entry, L 9..56, was the best fit.
 *
 * usage: java analysis.probe.ProbeBlockSearch DIR DIR/results/response_pitch.csv 9,10,...,56
 */
public final class ProbeBlockSearch {
  public static void main(String[] a) throws Exception {
    Path dir = Paths.get(a[0]); Path csv = Paths.get(a[1]);
    ProbePlan plan = ProbePlan.read(dir); List<byte[]> frames = ProbeSupport.readFrames(dir.resolve("frames.hex"));
    List<ProbePlan.Probe> pitch = plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.PITCH).toList();
    // chip dB per probe from the analyzer csv: rows come in probe order; a new probe starts when b0 changes
    List<Map<Integer, Double>> chip = new ArrayList<>(); String lastB0 = null;
    for (String line : Files.readAllLines(csv).subList(1, Files.readAllLines(csv).size())) {
      String[] f = line.split(","); if (!f[0].equals(lastB0)) { chip.add(new TreeMap<>()); lastB0 = f[0]; }
      chip.get(chip.size() - 1).put(Integer.parseInt(f[3]), Double.parseDouble(f[5]));
    }
    // probes with no gated harmonics never appear in the csv; align by b0 sequence
    List<Integer> csvB0 = new ArrayList<>(); lastB0 = null;
    for (String line : Files.readAllLines(csv).subList(1, Files.readAllLines(csv).size())) { String b = line.split(",")[0]; if (!b.equals(lastB0)) { csvB0.add(Integer.parseInt(b)); lastB0 = b; } }
    Map<Integer, Map<Integer, Double>> byProbe = new HashMap<>(); int k = 0;
    for (int i = 0; i < pitch.size() && k < csvB0.size(); i++) if (pitch.get(i).b0() == csvB0.get(k)) { byProbe.put(i, chip.get(k)); k++; }
    for (String Ls : a[2].split(",")) {
      int L = Integer.parseInt(Ls);
      int[] J = LMPRBlockLength.fromValue(L).getBlockLengths(); int[] orig = J.clone();
      List<int[]> results = new ArrayList<>(); List<String> labels = new ArrayList<>();
      for (int j1 = Math.max(1, orig[1] - 2); j1 <= orig[1] + 2; j1++) for (int j2 = Math.max(1, orig[2] - 2); j2 <= orig[2] + 2; j2++) for (int j3 = Math.max(1, orig[3] - 2); j3 <= orig[3] + 2; j3++) {
        int j4 = L - j1 - j2 - j3; if (j4 < 1 || j4 > 17 || j1 > 17 || j2 > 17 || j3 > 17 || Math.abs(j4 - orig[4]) > 3) continue;
        J[1] = j1; J[2] = j2; J[3] = j3; J[4] = j4;
        double ss = 0; int n = 0;
        for (int i = 0; i < pitch.size(); i++) {
          ProbePlan.Probe p = pitch.get(i); if (p.L() != L || !byProbe.containsKey(i)) continue;
          int from = Math.max(0, p.start - 20);
          List<AMBEModelParameters> par = ProbeSupport.decode(frames.subList(from, p.start + p.length));
          double[] m = new double[L + 1];
          for (int f = p.start + 10; f < p.start + 19; f++) { float[] e = par.get(f - from).getEnhancedSpectralAmplitudes(); for (int l = 1; l <= L; l++) m[l] += ProbeSupport.db(e[l]) / 9; }
          Map<Integer, Double> c = byProbe.get(i); double mean = 0; int cnt = 0;
          for (var e : c.entrySet()) { mean += e.getValue() - m[e.getKey()]; cnt++; } mean /= cnt;
          for (var e : c.entrySet()) { double r = e.getValue() - m[e.getKey()] - mean; ss += r * r; n++; }
        }
        results.add(new int[]{(int)Math.round(1e4 * Math.sqrt(ss / n)), j1, j2, j3, j4});
      }
      System.arraycopy(orig, 0, J, 0, J.length);
      results.sort(Comparator.comparingInt(x -> x[0]));
      int[] o = results.stream().filter(x -> x[1] == orig[1] && x[2] == orig[2] && x[3] == orig[3]).findFirst().get();
      System.out.printf("L %d: JMBE {%d,%d,%d,%d} RMS %.3f dB; best:", L, orig[1], orig[2], orig[3], orig[4], o[0] / 1e4);
      for (int t = 0; t < 4; t++) System.out.printf("  {%d,%d,%d,%d} %.3f", results.get(t)[1], results.get(t)[2], results.get(t)[3], results.get(t)[4], results.get(t)[0] / 1e4);
      System.out.println();
    }
  }
}
