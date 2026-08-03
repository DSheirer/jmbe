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

package jmbe;

import java.util.List;

/**
 * Record carrier to return the results.
 * @param jmbeAmplitudes amplitudes
 * @param ambeAmplitudes amplitudes
 * @param jmbePcm audio samples
 * @param ambePcm audio samples
 */
public record AnalysisResults(List<double[]> jmbeAmplitudes, List<double[]> jmbeEnhancedAmplitudes,
                              List<double[]> ambeAmplitudes, List<float[]> jmbePcm, List<float[]> ambePcm) {}
