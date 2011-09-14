//
// FlipVertically.java
//

/*
ImageJ software for multidimensional image processing and analysis.

Copyright (c) 2010, ImageJDev.org.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the names of the ImageJDev.org developers nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

package imagej.core.plugins.rotate;

import net.imglib2.RandomAccess;
import net.imglib2.img.Axes;
import net.imglib2.img.ImgPlus;
import net.imglib2.type.numeric.RealType;
import imagej.ImageJ;
import imagej.data.Dataset;
import imagej.data.Extents;
import imagej.data.Position;
import imagej.data.display.DisplayService;
import imagej.data.display.ImageDisplay;
import imagej.data.display.OverlayService;
import imagej.ext.plugin.ImageJPlugin;
import imagej.ext.plugin.Menu;
import imagej.ext.plugin.Parameter;
import imagej.ext.plugin.Plugin;
import imagej.util.RealRect;

/**
 * Modifies an input Dataset by flipping its pixels vertically. Flips all
 * image pixels unless a selected region is available from the OverlayService.
 * 
 * @author Barry DeZonia
 */
@Plugin(menu = {
	@Menu(label = "Image", mnemonic = 'i'),
	@Menu(label = "Transform", mnemonic = 't'),
	@Menu(label = "Flip Vertically", weight = 2) })
public class FlipVertically implements ImageJPlugin {

	// -- instance variables that are Parameters --

	@Parameter
	private ImageDisplay display;

	// -- public interface --

	@Override
	public void run() {
		Dataset input = ImageJ.get(DisplayService.class).getActiveDataset(display);
		RealRect selection =
			ImageJ.get(OverlayService.class).getSelectionBounds(display);
		flipPixels(input, selection);
	}

	// -- private interface --

	private void flipPixels(Dataset input, RealRect selection) {
		
		long[] dims = input.getDims();
		int xAxis = input.getAxisIndex(Axes.X);
		int yAxis = input.getAxisIndex(Axes.Y);
		if ((xAxis < 0) || (yAxis < 0))
			throw new IllegalArgumentException(
				"cannot flip image that does not have XY planes");
		
		long oX = 0;
		long oY = 0;
		long width = dims[xAxis];
		long height = dims[yAxis];
		
		if ((selection.width >= 1) && (selection.height >= 1)) {
			oX = (long) selection.x;
			oY = (long) selection.y;
			width = (long) selection.width;
			height = (long) selection.height;
		}

		long[] planeDims = new long[dims.length-2];
		int d = 0;
		for (int i = 0; i < dims.length; i++) {
			if (i == xAxis) continue;
			if (i == yAxis) continue;
			planeDims[d++] = dims[i];
		}
		
		Position planePos = new Extents(planeDims).createPosition();

		if (dims.length == 2) { // a single plane
			flipPlane(input, xAxis, yAxis, new long[]{}, oX, oY, width, height);
		}
		else { // has multiple planes
			long[] planeIndex = new long[planeDims.length];
			while (planePos.hasNext()) {
				planePos.fwd();
				planePos.localize(planeIndex);
				flipPlane(input, xAxis, yAxis, planeIndex, oX, oY, width, height);
			}
		}
		input.update();
	}
	
	private void flipPlane(Dataset input, int xAxis, int yAxis,
		long[] planeIndex, long oX, long oY, long width, long height)
	{
		if (height == 1) return;
		
		ImgPlus<? extends RealType<?>> imgPlus = input.getImgPlus();
		
		RandomAccess<? extends RealType<?>> acc1 = imgPlus.randomAccess();
		RandomAccess<? extends RealType<?>> acc2 = imgPlus.randomAccess();
		
		long[] pos1 = new long[planeIndex.length+2];
		long[] pos2 = new long[planeIndex.length+2];

		int d = 0;
		for (int i = 0; i < pos1.length; i++) {
			if (i == xAxis) continue;
			if (i == yAxis) continue;
			pos1[i] = planeIndex[d];
			pos2[i] = planeIndex[d];
			d++;
		}

		long row1, row2;
		
		if ((height & 1) == 0) { // even number of rows
			row2 = height/2;
			row1 = row2 - 1;
		}
		else { // odd number of rows
			row2 = height/2 + 1;
			row1 = row2 - 2;
		}
		
		while (row1 >= 0) {
			pos1[yAxis] = oY + row1;
			pos2[yAxis] = oY + row2;
			for (long x = oX; x < oX+width; x++) {
				pos1[xAxis] = x;
				pos2[xAxis] = x;
				acc1.setPosition(pos1);
				acc2.setPosition(pos2);
				double value1 = acc1.get().getRealDouble();
				double value2 = acc2.get().getRealDouble();
				acc1.get().setReal(value2);
				acc2.get().setReal(value1);
			}
			row1--;
			row2++;
		}
	}
}
