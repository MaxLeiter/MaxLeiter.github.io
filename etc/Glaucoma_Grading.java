import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;

public class Glaucoma_Grading implements PlugIn, KeyListener {
	private ImageWindow win;
	private ImageCanvas can;
	private RoiManager manager;
	private final Set<Integer> pressed = new HashSet<Integer>(2); // For keybinds with multiple keys
	private final static boolean debug = false; // enables more verbose logging
	/**
	 * ImageJ hooks into run() on plugin initialization.
	 * Here, we register the plugin and call our local start() function.
	 * "arg" is an optional ImageJ command to be passed in by other plugins
	 */
	public void run(String arg) {
		IJ.log("Glaucoma Grading plugin started");
		IJ.register(Glaucoma_Grading.class);
		start(IJ.getImage());
	}

	/**
	 * Initialize the open Image and assign global variables.
	 * @param ImagePlus
	 */
	private void start(ImagePlus imp) {
		ImagePlus img = imp;
		win = img.getWindow();
		can = win.getCanvas();
		can.removeKeyListener(IJ.getInstance()); // To fix conflicts on this image
		can.addKeyListener(this);
		if (manager == null) { // If the ROI manager isn't already present, create an instance of it.
			manager = new RoiManager();
		}
	}

	/**
	 * @param message - the log message to print
	 */
	private void debug(String message) {
		if (debug) {
			IJ.log(message);
		}
	}

	/**
	 * Event fired when a key press occurs
	 */
	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode(); // Key pressed
		pressed.add(keyCode); // Add to HashSet, which is used for multiple key presses

		ImagePlus img = win.getImagePlus();
		debug("keyPressed: keyCode=" + keyCode + " (" + KeyEvent.getKeyText(keyCode) + ")");
		if (img.getRoi() != null) { // ensure there's an ROI to add
			if (keyCode == 16 || keyCode == 10) { // SHIFT || ENTER
				if (pressed.size() == 2) { // ENTER && SHIFT
					manager.addRoi(img.getRoi()); // add our ROI
					manager.toFront(); // Bring the manager to the front of the windows
					manager.requestFocus(); // Request that the manager is focused (i.e. cursor moved/clicked). Necessary for fetching ROIs, for some reason.
					IJ.log("ROI added to manager");
					if (manager.getRoisAsArray().length >= 2) { // If two ROIs (cup and disc, hopefully) are added. Ideally, length should never be greater than 2.
						IJ.log("Two ROI detected. Calculating...");
						calculateResults();
						manager.reset(); // Remove entries from ROI Manager for the next image.
					}
					return;
				}
			} else {
				pressed.clear(); // The keys pressed are not SHIFT or ENTER, so clear the pressed map.
			}
		}
	}

	private void calculateResults() {
		IJ.log("Calculating...");

		if (manager != null) { // The below is more inefficient then I would like, but the calculations done are minimal
			ArrayList<Double> heights = new ArrayList<Double>();
			ArrayList<Double> widths = new ArrayList<Double>();
			ArrayList<Double> areas = new ArrayList<Double>();

			List<Roi> rois = Arrays.asList(manager.getRoisAsArray()); // To make iteration a little easier
			for (Roi r : rois) { // Iterate over each polygon present
				Polygon poly = r.getPolygon();
				Rectangle points = poly.getBounds();
				heights.add(points.getHeight());
				widths.add(points.getWidth());
				areas.add(area(poly.xpoints, poly.ypoints, poly.npoints));
			}

			// Sort ArrayList's so order of creating C:D doesn't matter to grader. Done in-place, hence no assignment.
			Collections.sort(heights);
			Collections.sort(widths);
			Collections.sort(areas);

			double vertCD, horzCD, areaRt;

			vertCD = heights.get(0) / heights.get(1);
			horzCD = widths.get(0) / widths.get(1);
			debug("Area 0: " + areas.get(0).toString());
			debug("Area 1: " + areas.get(1).toString());
			areaRt = Math.sqrt(areas.get(0) / areas.get(1));
			debug("Area ratio: " + areaRt);
			IJ.log("Done calculating.");
			showResults(vertCD, horzCD, areaRt);
		}
	}

	/**
	 * Displays the Results window with the new C:D ratio appended to existing results.
	 * @param vertical ratio
	 * @param horizontal ratio
	 * @param area ratio
	 */
	private void showResults(double vert, double horizontal, double areaRatio) {
		IJ.log("Displaying results for " + this.win.getImagePlus().getTitle());
		ResultsTable rt = Analyzer.getResultsTable();
		if (rt == null) { // If the ResultTable is not already present, create it
			rt = new ResultsTable();
		}
		rt.incrementCounter(); // Necessary due to ImageJ weirdness; if not present, it will over-ride the exisiting results.
		Analyzer.setResultsTable(rt); // Need to bind this results table, as multiple can be open.
		rt.addValue("Image", this.win.getImagePlus().getTitle());
		rt.addValue("Vertical C:D", vert);
		rt.addValue("Horizontal C:D", horizontal);
		rt.addValue("Sqrt Area Ratio", areaRatio);
		rt.show("Results"); // Title of the window
		ResultsTable.getResultsWindow().requestFocus(); // Bring to front and select
	}

	/**
	 * Signed area of a polygon using the shoelace formula
	 * Works on any non-self-intersecting polygon
	 * @param x[]
	 * @param y[]
	 * @param number of sides
	 * https://en.wikipedia.org/wiki/Shoelace_formula
	 * @return double area
	 */
	public double area(int[] x, int[] y, int n) {
		double area = 0.0;

		int j;
		for (int i = 0; i < n; i++) {
			j = (i + 1) % n;
			area += x[i] * y[j] - x[j] * y[i];
		}

		return Math.abs(area / 2.0);
	}

	@Override
	public void keyTyped(KeyEvent e) {

	}

	@Override
	public void keyReleased(KeyEvent e) {

	}

}