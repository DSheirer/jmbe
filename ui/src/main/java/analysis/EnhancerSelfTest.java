package analysis;

import java.nio.file.*;
import java.util.*;

/**
 * Checks the integration pieces against the evaluated path.
 *   java EnhancerSelfTest <params-dir> <model.txt> [generatedModelClassNameCheck]
 * 1. file-loaded model == model re-parsed from its saved text   2. enhanceCall == the AmbeAudioCompare per-frame path
 * 3. Stream == enhanceCall (incl. gaps)   4. JMBE layout round trip.
 */
public final class EnhancerSelfTest {
    static int fails = 0;
    static void check(String what, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]);
        LearnedEnhancer.Mlp net = LearnedEnhancer.Mlp.load(Paths.get(a[1]));
        Path tmp = Files.createTempFile("mlp", ".txt");
        net.save(tmp);
        LearnedEnhancer.Mlp re = LearnedEnhancer.Mlp.parse(Files.readString(tmp));
        double[] probe = new double[LearnedEnhancer.NF];
        Random r = new Random(1);
        for (int i = 0; i < probe.length; i++) probe[i] = r.nextGaussian();
        check("save/parse round trip identical", net.predict(probe) == re.predict(probe) && net.ctx == re.ctx);

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.params.txt")) { for (Path p : ds) files.add(p); }
        Collections.sort(files);
        double worstBatch = 0, worstStream = 0;
        int frames = 0, gaps = 0;
        for (int c = 0; c < Math.min(5, files.size()); c++) {
            String n = files.get(c).getFileName().toString();
            List<AmbeEnhancementAnalyzer.Frame> fr = AmbeEnhancementAnalyzer.loadParams(files.get(c), n, c);
            // drop a few frames to create gaps
            List<AmbeEnhancementAnalyzer.Frame> use = new ArrayList<>();
            for (int i = 0; i < fr.size(); i++) if (i % 37 != 5) use.add(fr.get(i)); else gaps++;
            int N = use.size();
            int[] idx = new int[N]; double[][] m = new double[N][]; boolean[][] v = new boolean[N][]; double[] w = new double[N];
            for (int i = 0; i < N; i++) { AmbeEnhancementAnalyzer.Frame f = use.get(i); idx[i] = f.idx; m[i] = f.m; v[i] = f.v; w[i] = f.w0; }
            double[][] batch = FrameSequenceEnhancer.enhanceCall(net, idx, m, v, w);
            // reference: the exact loop AmbeAudioCompare uses
            for (int i = 0; i < N; i++) {
                double[][] nm = new double[4][]; double[] nw = new double[4]; int[] off = {-2, -1, 1, 2};
                for (int k = 0; k < 4; k++) { int j = i + off[k]; if (j < 0 || j >= N || use.get(j).idx != use.get(i).idx + off[k]) continue; nm[k] = use.get(j).m; nw[k] = use.get(j).w0; }
                double[] ref = LearnedEnhancer.enhance(net, m[i], v[i], w[i], nm, nw);
                for (int l = 0; l < ref.length; l++) worstBatch = Math.max(worstBatch, Math.abs(ref[l] - batch[i][l]));
            }
            FrameSequenceEnhancer.Stream s = new FrameSequenceEnhancer.Stream(net);
            List<FrameSequenceEnhancer.Output> outs = new ArrayList<>();
            for (int i = 0; i < N; i++) outs.addAll(s.push(idx[i], m[i], v[i], w[i]));
            outs.addAll(s.flush());
            boolean order = outs.size() == N;
            for (int i = 0; order && i < N; i++) {
                order = outs.get(i).frameNumber == idx[i];
                for (int l = 0; l < batch[i].length; l++) worstStream = Math.max(worstStream, Math.abs(outs.get(i).amplitudes[l] - batch[i][l]));
            }
            check("call " + c + ": stream emits every frame in order", order);
            frames += N;
        }
        check(String.format("batch == evaluated path (max diff %.2e over %d frames, %d gaps)", worstBatch, frames, gaps), worstBatch == 0);
        check(String.format("stream == batch (max diff %.2e)", worstStream), worstStream == 0);

        float[] j = {0f, 3f, 2f, 1f};
        double[] z = FrameSequenceEnhancer.toZeroBased(j);
        float[] back = FrameSequenceEnhancer.toJmbe(z, j);
        check("JMBE layout round trip", Arrays.equals(j, back) && z.length == 3 && z[0] == 3.0);
        double[] e = LearnedEnhancer.enhance(net, new double[]{10, 8, 6, 4, 3, 2}, new boolean[]{true, true, true, false, false, false}, 0.2, null, null);
        boolean fin = true; for (double d : e) fin &= Double.isFinite(d) && d > 0;
        check("no neighbors (first frame of a call): finite positive output", fin);
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        Files.deleteIfExists(tmp);
        if (fails != 0) System.exit(1);
    }
}
