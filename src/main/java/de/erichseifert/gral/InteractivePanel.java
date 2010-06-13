/*
 * GRAL: Vector export for Java(R) Graphics2D
 *
 * (C) Copyright 2009-2010 Erich Seifert <info[at]erichseifert.de>, Michael Seifert <michael.seifert[at]gmx.net>
 *
 * This file is part of GRAL.
 *
 * GRAL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GRAL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GRAL.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.erichseifert.gral;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;

import de.erichseifert.gral.io.IOCapabilities;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.Plot;
import de.erichseifert.gral.plots.PlotNavigator;
import de.erichseifert.gral.plots.XYPlot;
import de.erichseifert.gral.plots.axes.Axis;
import de.erichseifert.gral.plots.axes.AxisRenderer2D;


/**
 * A class that displays a <code>Drawable</code> instance as a rich Swing component.
 */
public class InteractivePanel extends DrawablePanel implements Printable {
	private static final long serialVersionUID = 1L;

	// FIXME: Find better method to adjust resolution
	private static final double MM_TO_PT = 72.0/25.4;      // mm -> pt
	private static final double MM_PER_PX = 0.2*MM_TO_PT;  // 1px = 0.2mm
	private final PrinterJob printerJob;

	private static final int MIN_DRAG = 0;

	private final JPopupMenu menu;
	private final JMenuItem refresh;
	private final JMenuItem resetZoom;
	private final JMenuItem exportImage;
	private final JMenuItem print;

	private final JFileChooser exportImageChooser;

	private PlotNavigator navigator;

	/**
	 * Creates a new panel instance and initializes it with a drawable component.
	 * @param drawable drawable component.
	 */
	public InteractivePanel(Drawable drawable) {
		super(drawable);

		printerJob = PrinterJob.getPrinterJob();
		printerJob.setPrintable(this);

		IOCapabilities[] exportFormats = DrawableWriterFactory.getInstance().getCapabilities();
		exportImageChooser = new ExportChooser(exportFormats);
		exportImageChooser.setDialogTitle("Export image");

		menu = new JPopupMenu();

		refresh = new JMenuItem(new AbstractAction("Refresh") {
			@Override
			public void actionPerformed(ActionEvent e) {
				repaint();
			}
		});
		menu.add(refresh);

		menu.addSeparator();

		resetZoom = new JMenuItem(new AbstractAction("Reset view") {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (navigator != null) {
					navigator.reset();
					repaint();
				}
			}
		});
		menu.add(resetZoom);

		menu.addSeparator();

		exportImage = new JMenuItem(new AbstractAction("Export image...") {
			@Override
			public void actionPerformed(ActionEvent e) {
				int ret = exportImageChooser.showSaveDialog(InteractivePanel.this);
				if (ret == JFileChooser.APPROVE_OPTION) {
					Drawable d = getDrawable();
					ExportDialog ed = new ExportDialog(InteractivePanel.this, d);
					ed.setVisible(true);
					if (ed.getUserAction().equals(ExportDialog.UserAction.APPROVE)) {
						File file = exportImageChooser.getSelectedFile();
						if (exportImageChooser.getSelectedFile() == null) {
							return;
						}
						DrawableWriterFilter filter = (DrawableWriterFilter) exportImageChooser.getFileFilter();
						export(d, filter.getWriterCapabilities().getMimeType(), file, ed.getDocumentBounds());
					}
				}
			}
		});
		menu.add(exportImage);

		print = new JMenuItem(new AbstractAction("Print...") {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (printerJob.printDialog()) {
					try {
						printerJob.print();
					} catch (PrinterException ex) {
						// TODO Show error dialog
						ex.printStackTrace();
					}
				}
			}
		});
		menu.add(print);

		addMouseListener(new PopupListener());

		if (getDrawable() instanceof Plot) {
			navigator = new PlotNavigator((Plot) getDrawable());
			// Register a new handler to zoom the map with the mouse wheel
			addMouseWheelListener(new MouseWheelListener() {
				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					double zoomNew = navigator.getZoom()*Math.pow(1.25, e.getWheelRotation());
					navigator.setZoom(zoomNew);
					repaint();
				}
			});

			if (getDrawable() instanceof XYPlot) {
				// Register a new handler to move the map by dragging
				// This requires that an x- and a y-axis do exist in the plot
				MoveListener moveListener = new MoveListener((XYPlot) getDrawable());
				addMouseListener(moveListener);
				addMouseMotionListener(moveListener);
			}
		}
	}

	private void export(Drawable d, String mimeType, File f, Rectangle2D documentBounds) {
		FileOutputStream destination;
		try {
			destination = new FileOutputStream(f);
		} catch (FileNotFoundException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
			return;
		}
		DrawableWriter w = DrawableWriterFactory.getInstance().get(mimeType);
		try {
			w.write(d, destination, documentBounds.getX(), documentBounds.getY(),
					documentBounds.getWidth(), documentBounds.getHeight());
		} catch (IOException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		} finally {
			try {
				destination.close();
			} catch (IOException ex2) {
				// TODO Auto-generated catch block
				ex2.printStackTrace();
			}
		}
	}

	private class PopupListener extends MouseAdapter {
	    @Override
		public void mousePressed(MouseEvent e) {
	        showPopup(e);
	    }

	    @Override
		public void mouseReleased(MouseEvent e) {
	        showPopup(e);
	    }

	    private void showPopup(MouseEvent e) {
	        if (e.isPopupTrigger()) {
	            menu.show(e.getComponent(), e.getX(), e.getY());
	        }
	    }
	}

	private class MoveListener extends MouseAdapter {
		private final Plot plot;
		private Point posPrev;

		/**
		 * Creates a new listener and initializes it with a plot.
		 * @param plot plot that should be changed.
		 */
		public MoveListener(Plot plot) {
			this.plot = plot;
		}

		@Override
		public void mousePressed(MouseEvent e) {
			posPrev = e.getPoint();
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			// Calculate distance that the current view was dragged
			// (screen units)
			Point pos = e.getPoint();
			int dx = -pos.x + posPrev.x;
			int dy =  pos.y - posPrev.y;
			posPrev = pos;

			if (Math.abs(dx) > MIN_DRAG || Math.abs(dy) > MIN_DRAG) {
				Axis axisX = plot.getAxis(Axis.X);
				Axis axisY = plot.getAxis(Axis.Y);
				AxisRenderer2D axisXRenderer =
					plot.<AxisRenderer2D>getSetting(XYPlot.AXIS_X_RENDERER);
				AxisRenderer2D axisYRenderer =
					plot.<AxisRenderer2D>getSetting(XYPlot.AXIS_Y_RENDERER);

				// Fetch current center on screen
				double centerX = axisXRenderer.worldToView(
					axisX, navigator.getCenter(axisX), true);
				double centerY = axisYRenderer.worldToView(
					axisY, navigator.getCenter(axisY), true);

				// Move center and convert it to axis coordinates
				Number centerXNew = axisXRenderer.viewToWorld(
					axisX, centerX + dx, true);
				Number centerYNew = axisYRenderer.viewToWorld(
					axisY, centerY + dy, true);

				// Change axes (world units)
				navigator.setCenter(axisX, centerXNew);
				navigator.setCenter(axisY, centerYNew);

				// Refresh display
				repaint();
			}
		}
	}

	/**
	 * File filter that extracts files that can be read with a certain set of {@link IOCapabilities}.
	 */
	public final static class DrawableWriterFilter extends FileFilter {
		private final IOCapabilities capabilities;

		/**
		 * Creates a new instance and initializes it with an {@link IOCapabilities} object.
		 * @param capabilities writer capabilities.
		 */
		public DrawableWriterFilter(IOCapabilities capabilities) {
			this.capabilities = capabilities;
		}

		@Override
		public boolean accept(File f) {
			if (f.isDirectory()) {
				return true;
			}
			String ext = getExtension(f).toLowerCase();
			for (String extension : capabilities.getExtensions()) {
				if (extension.equals(ext)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public String getDescription() {
			return String.format("%s: %s", capabilities.getFormat(), capabilities.getName());
		}

		/**
		 * Returns the capabilities filtered by this instance.
		 * @return writer capabilities.
		 */
		public IOCapabilities getWriterCapabilities() {
			return capabilities;
		}

		private final static String getExtension(File f) {
			String name = f.getName();
			int lastDot = name.lastIndexOf('.');
			if ((lastDot <= 0) || (lastDot == name.length() - 1)) {
				return "";
			}
			return name.substring(lastDot + 1);
		}
	}

	/**
	 * A file chooser implementation that can be for export purposes.
	 */
	public final static class ExportChooser extends JFileChooser {
		/**
		 * Creates a new instance and initializes it with an array of IOCapabilities.
		 * @param capabilities Array of objects describing the file formats
		 * that are supported by this dialog.
		 */
		public ExportChooser(IOCapabilities... capabilities) {
			setAcceptAllFileFilterUsed(false);
			for (IOCapabilities c : capabilities) {
				addChoosableFileFilter(new DrawableWriterFilter(c));
			}
		}
	}

	/**
	 * A dialog implementation for exporting plots. It allows the user to
	 * specify the document dimensions.
	 */
	public final static class ExportDialog extends JDialog {
		/** Type of user feedback. */
		public static enum UserAction {
			/** User confirmed dialog. */
			APPROVE,
			/** User canceled or closed dialog. */
			CANCEL
		};

		private final Rectangle2D documentBounds;
		private UserAction userAction;

		private final JFormattedTextField inputX;
		private final JFormattedTextField inputY;
		private final JFormattedTextField inputW;
		private final JFormattedTextField inputH;

		/**
		 * Creates a new instance and initializes it with a parent and a
		 * drawable component.
		 * @param parent Parent component.
		 * @param d Drawable component.
		 */
		public ExportDialog(Component parent, Drawable d) {
			super(JOptionPane.getFrameForComponent(parent), true);
			setTitle("Export options");

			documentBounds = new Rectangle2D.Double();
			documentBounds.setFrame(d.getBounds());
			userAction = UserAction.CANCEL;

			JPanel cp = new JPanel(new BorderLayout());
			cp.setBorder(new EmptyBorder(10, 10, 10, 10));
			setContentPane(cp);

			DecimalFormat formatMm = new DecimalFormat();
			formatMm.setMinimumFractionDigits(2);

			JPanel options = new JPanel(new GridLayout(4, 2, 10, 2));
			getContentPane().add(options, BorderLayout.NORTH);

			PropertyChangeListener docBoundsListener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					documentBounds.setFrame(
						((Number)inputX.getValue()).doubleValue(),
						((Number)inputY.getValue()).doubleValue(),
						((Number)inputW.getValue()).doubleValue(),
						((Number)inputH.getValue()).doubleValue());
				}
			};
			inputX = new JFormattedTextField(formatMm);
			addInputField(inputX, "Left", options, documentBounds.getX(), docBoundsListener);
			inputY = new JFormattedTextField(formatMm);
			addInputField(inputY, "Top", options, documentBounds.getY(), docBoundsListener);
			inputW = new JFormattedTextField(formatMm);
			addInputField(inputW, "Width", options, documentBounds.getWidth(), docBoundsListener);
			inputH = new JFormattedTextField(formatMm);
			addInputField(inputH, "Height", options, documentBounds.getHeight(), docBoundsListener);

			JPanel controls = new JPanel(new FlowLayout());
			cp.add(controls, BorderLayout.SOUTH);

			JButton buttonConfirm = new JButton("OK");
			buttonConfirm.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					userAction = UserAction.APPROVE;
					dispose();
				}
			});
			controls.add(buttonConfirm);

			JButton buttonCancel = new JButton("Cancel");
			buttonCancel.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					userAction = UserAction.CANCEL;
					dispose();
				}
			});
			controls.add(buttonCancel);

			pack();
			setLocationRelativeTo(parent);
		}

		private static void addInputField(JFormattedTextField input,
				String labelText, java.awt.Container cont, Object initialValue,
				PropertyChangeListener pcl) {
			JLabel label = new JLabel(labelText);
			label.setHorizontalAlignment(JLabel.RIGHT);
			cont.add(label);
			input.setValue(initialValue);
			input.setHorizontalAlignment(JFormattedTextField.RIGHT);
			input.addPropertyChangeListener("value", pcl);
			cont.add(input);
			label.setLabelFor(input);
		}

		/**
		 * Returns the bounds entered by the user.
		 * @return Document bounds that should be used to export the plot
		 */
		public Rectangle2D getDocumentBounds() {
			Rectangle2D bounds = new Rectangle2D.Double();
			bounds.setFrame(documentBounds);
			return bounds;
		}
		public UserAction getUserAction() {
			return userAction;
		}
	}

	@Override
	public int print(Graphics g, PageFormat pageFormat, int pageIndex)
			throws PrinterException {
		if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }

		Graphics2D g2d = (Graphics2D) g;
		AffineTransform txOld = g2d.getTransform();
		g2d.scale(MM_PER_PX, MM_PER_PX);

		Rectangle2D boundsOld = getDrawable().getBounds();
		Rectangle2D pageBounds = new Rectangle2D.Double(
				pageFormat.getImageableX()/MM_PER_PX, pageFormat.getImageableY()/MM_PER_PX,
				pageFormat.getImageableWidth()/MM_PER_PX, pageFormat.getImageableHeight()/MM_PER_PX);

		// Set size
		// TODO: Keep Drawable's aspect ratio when scaling
		getDrawable().setBounds(pageBounds);
		// TODO: Be sure to temporarily turn off antialiasing before printing
		try {
			getDrawable().draw(g2d);
		} finally {
			getDrawable().setBounds(boundsOld);
		}
		g2d.setTransform(txOld);
		return Printable.PAGE_EXISTS;
	}

}
