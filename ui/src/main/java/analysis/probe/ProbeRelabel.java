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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Re-labels a probe run made before the u3 bit-order fix. Its frames.hex was packed with b3[0] ahead of b4[2:0],
 * so the indices the chip decoded differ from the manifest. This writes a copy of the run whose manifest holds the
 * indices JMBE's (fixed) decoder reads from each probe's frame, which are the ones the chip used, so the old
 * chip.pcm can be analyzed again without the hardware.
 *
 * usage: java analysis.probe.ProbeRelabel OLD_DIR NEW_DIR
 */
public final class ProbeRelabel
{
    public static void main(String[] args) throws Exception
    {
        if(args.length != 2)
        {
            System.err.println("usage: ProbeRelabel OLD_DIR NEW_DIR");
            System.exit(1);
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        ProbePlan plan = ProbePlan.read(in); // plan.frames holds each frame decoded with the current AMBEFrame
        List<String> lines = Files.readAllLines(in.resolve("manifest.csv"));
        List<String> relabeled = new ArrayList<>();
        relabeled.add(lines.get(0));
        int changed = 0;
        for(int i = 1; i < lines.size(); i++)
        {
            String[] f = lines.get(i).split(",");
            if(f.length < 16)
            {
                continue;
            }
            int[] b = plan.frames.get(Integer.parseInt(f[4]));
            ProbePlan.Kind kind = ProbePlan.Kind.valueOf(f[1]);
            int index = kind.bIndex() >= 0 ? b[kind.bIndex()] : Integer.parseInt(f[3]);
            StringBuilder sb = new StringBuilder();
            sb.append(f[0]).append(',').append(f[1]).append(',').append(f[2]).append(',').append(index).append(',')
                .append(f[4]).append(',').append(f[5]).append(',').append(b[0]).append(',').append(f[7]);
            for(int k = 1; k < 9; k++)
            {
                sb.append(',').append(b[k]);
            }
            if(!sb.toString().equals(lines.get(i)))
            {
                changed++;
            }
            relabeled.add(sb.toString());
        }
        Files.write(out.resolve("manifest.csv"), relabeled);
        for(String name : new String[]{"frames.hex", "chip.pcm"})
        {
            if(Files.exists(in.resolve(name)))
            {
                Files.copy(in.resolve(name), out.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        System.out.println(changed + " of " + (relabeled.size() - 1) + " probes relabeled; wrote " + out);
    }
}
