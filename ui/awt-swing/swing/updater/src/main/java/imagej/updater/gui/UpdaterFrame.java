//
// UpdaterFrame.java
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

package imagej.updater.gui;

import imagej.updater.core.Checksummer;
import imagej.updater.core.FileObject;
import imagej.updater.core.FileObject.Action;
import imagej.updater.core.FileObject.Status;
import imagej.updater.core.FilesCollection;
import imagej.updater.core.FilesCollection.DependencyMap;
import imagej.updater.core.FilesUploader;
import imagej.updater.core.Installer;
import imagej.updater.util.Canceled;
import imagej.updater.util.Progress;
import imagej.updater.util.StderrProgress;
import imagej.updater.util.UserInterface;
import imagej.updater.util.Util;
import imagej.util.Log;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

@SuppressWarnings("serial")
public class UpdaterFrame extends JFrame implements TableModelListener,
	ListSelectionListener
{

	protected FilesCollection files;

	protected JTextField txtSearch;
	protected ViewOptions viewOptions;
	protected FileTable table;
	protected JLabel fileSummary;
	protected FileDetails fileDetails;
	protected JButton apply, cancel, easy, updateSites;
	protected boolean easyMode;

	// For developers
	protected JButton upload, showChanges, rebuildButton;
	boolean canUpload;
	protected boolean hidden;

	public UpdaterFrame(final FilesCollection files) {
		this(files, false);
	}

	public UpdaterFrame(final FilesCollection files, final boolean hidden) {
		super("ImageJ Updater");
		setPreferredSize(new Dimension(780, 560));

		this.files = files;
		this.hidden = hidden;

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(final WindowEvent e) {
				quit();
			}
		});

		// ======== Start: LEFT PANEL ========
		final JPanel leftPanel = new JPanel();
		final GridBagLayout gb = new GridBagLayout();
		leftPanel.setLayout(gb);
		final GridBagConstraints c = new GridBagConstraints(0, 0, // x, y
			9, 1, // rows, cols
			0, 0, // weightx, weighty
			GridBagConstraints.NORTHWEST, // anchor
			GridBagConstraints.HORIZONTAL, // fill
			new Insets(0, 0, 0, 0), 0, 0); // ipadx, ipady

		txtSearch = new JTextField();
		txtSearch.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void changedUpdate(final DocumentEvent e) {
				updateFilesTable();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				updateFilesTable();
			}

			@Override
			public void insertUpdate(final DocumentEvent e) {
				updateFilesTable();
			}
		});
		final JPanel searchPanel =
			SwingTools.labelComponentRigid("Search:", txtSearch);
		gb.setConstraints(searchPanel, c);
		leftPanel.add(searchPanel);

		Component box = Box.createRigidArea(new Dimension(0, 10));
		c.gridy = 1;
		gb.setConstraints(box, c);
		leftPanel.add(box);

		viewOptions = new ViewOptions();
		viewOptions.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				updateFilesTable();
			}
		});

		final JPanel viewOptionsPanel =
			SwingTools.labelComponentRigid("View Options:", viewOptions);
		c.gridy = 2;
		gb.setConstraints(viewOptionsPanel, c);
		leftPanel.add(viewOptionsPanel);

		box = Box.createRigidArea(new Dimension(0, 10));
		c.gridy = 3;
		gb.setConstraints(box, c);
		leftPanel.add(box);

		// Create labels to annotate table
		final JPanel chooseLabel =
			SwingTools.label("Please choose what you want to install/uninstall:",
				null);
		c.gridy = 4;
		gb.setConstraints(chooseLabel, c);
		leftPanel.add(chooseLabel);

		box = Box.createRigidArea(new Dimension(0, 5));
		c.gridy = 5;
		gb.setConstraints(box, c);
		leftPanel.add(box);

		// Label text for file summaries
		fileSummary = new JLabel();
		final JPanel summaryPanel = SwingTools.horizontalPanel();
		summaryPanel.add(fileSummary);
		summaryPanel.add(Box.createHorizontalGlue());

		// Create the file table and set up its scrollpane
		table = new FileTable(this);
		table.getSelectionModel().addListSelectionListener(this);
		final JScrollPane fileListScrollpane = new JScrollPane(table);
		fileListScrollpane.getViewport().setBackground(table.getBackground());

		c.gridy = 6;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		gb.setConstraints(fileListScrollpane, c);
		leftPanel.add(fileListScrollpane);

		box = Box.createRigidArea(new Dimension(0, 5));
		c.gridy = 7;
		c.weightx = 0;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		gb.setConstraints(box, c);
		leftPanel.add(box);

		// ======== End: LEFT PANEL ========

		// ======== Start: RIGHT PANEL ========
		final JPanel rightPanel = SwingTools.verticalPanel();

		rightPanel.add(Box.createVerticalGlue());

		fileDetails = new FileDetails(this);
		SwingTools.tab(fileDetails, "Details", "Individual file information", 350,
			315, rightPanel);
		// TODO: put this into SwingTools, too
		rightPanel.add(Box.createRigidArea(new Dimension(0, 25)));
		// ======== End: RIGHT PANEL ========

		// ======== Start: TOP PANEL (LEFT + RIGHT) ========
		final JPanel topPanel = SwingTools.horizontalPanel();
		topPanel.add(leftPanel);
		topPanel.add(Box.createRigidArea(new Dimension(15, 0)));
		topPanel.add(rightPanel);
		topPanel.setBorder(BorderFactory.createEmptyBorder(20, 15, 5, 15));
		// ======== End: TOP PANEL (LEFT + RIGHT) ========

		// ======== Start: BOTTOM PANEL ========
		final JPanel bottomPanel2 = SwingTools.horizontalPanel();
		final JPanel bottomPanel = SwingTools.horizontalPanel();
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));
		bottomPanel.add(new FileAction("Keep as-is", null));
		bottomPanel.add(Box.createRigidArea(new Dimension(15, 0)));
		bottomPanel.add(new FileAction("Install", Action.INSTALL, "Update",
			Action.UPDATE));
		bottomPanel.add(Box.createRigidArea(new Dimension(15, 0)));
		bottomPanel.add(new FileAction("Uninstall", Action.UNINSTALL));

		bottomPanel.add(Box.createHorizontalGlue());

		// Button to start actions
		apply =
			SwingTools.button("Apply changes", "Start installing/uninstalling files",
				new ActionListener() {

					@Override
					public void actionPerformed(final ActionEvent e) {
						applyChanges();
					}
				}, bottomPanel);
		apply.setEnabled(false);

		// Manage update sites
		updateSites =
			SwingTools.button("Manage update sites",
				"Manage multiple update sites for updating and uploading",
				new ActionListener() {

					@Override
					public void actionPerformed(final ActionEvent e) {
						new SitesDialog(UpdaterFrame.this, UpdaterFrame.this.files,
							UpdaterFrame.this.files.hasUploadableSites()).setVisible(true);
					}
				}, bottomPanel2);

		// includes button to upload to server if is a Developer using
		bottomPanel2.add(Box.createRigidArea(new Dimension(15, 0)));
		upload =
			SwingTools.button("Upload to server", "Upload selected files to server",
				new ActionListener() {

					@Override
					public void actionPerformed(final ActionEvent e) {
						new Thread() {

							@Override
							public void run() {
								try {
									upload();
								}
								catch (final InstantiationException e) {
									Log.error(e);
									error("Could not upload (possibly unknown protocol)");
								}
							}
						}.start();
					}
				}, bottomPanel2);
		upload.setEnabled(false);
		upload.setVisible(files.hasUploadableSites());

		if (files.hasUploadableSites()) {
			final IJ1Plugin fileChanges =
				IJ1Plugin.discover("fiji.scripting.ShowPluginChanges");
			if (fileChanges != null &&
				new File(System.getProperty("ij.dir"), ".git").isDirectory())
			{
				bottomPanel2.add(Box.createRigidArea(new Dimension(15, 0)));
				showChanges =
					SwingTools.button("Show changes",
						"Show the changes in Git since the last upload",
						new ActionListener() {

							@Override
							public void actionPerformed(final ActionEvent e) {
								new Thread() {

									@Override
									public void run() {
										for (final FileObject file : table.getSelectedFiles())
											fileChanges.run(file.filename);
									}
								}.start();
							}
						}, bottomPanel2);
			}
			final IJ1Plugin rebuild =
				IJ1Plugin.discover("fiji.scripting.RunFijiBuild");
			if (rebuild != null &&
				new File(System.getProperty("ij.dir"), ".git").isDirectory())
			{
				bottomPanel2.add(Box.createRigidArea(new Dimension(15, 0)));
				rebuildButton =
					SwingTools.button("Rebuild", "Rebuild using Fiji Build",
						new ActionListener() {

							@Override
							public void actionPerformed(final ActionEvent e) {
								new Thread() {

									@Override
									public void run() {
										String list = "";
										final List<String> names = new ArrayList<String>();
										for (final FileObject file : table.getSelectedFiles()) {
											list +=
												("".equals(list) ? "" : " ") + file.filename +
													"-rebuild";
											names.add(file.filename);
										}
										if (!"".equals(list)) rebuild.run(list);
										final Checksummer checksummer =
											new Checksummer(files,
												getProgress("Checksumming rebuilt files"));
										checksummer.updateFromLocal(names);
										filesChanged();
										updateFilesTable();
									}
								}.start();
							}
						}, bottomPanel2);
			}
		}

		bottomPanel2.add(Box.createHorizontalGlue());

		bottomPanel.add(Box.createRigidArea(new Dimension(15, 0)));
		easy =
			SwingTools.button("Toggle easy mode",
				"Toggle between easy and verbose mode", new ActionListener() {

					@Override
					public void actionPerformed(final ActionEvent e) {
						toggleEasyMode();
					}
				}, bottomPanel);

		bottomPanel.add(Box.createRigidArea(new Dimension(15, 0)));
		cancel =
			SwingTools.button("Close", "Exit Update Manager", new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					quit();
				}
			}, bottomPanel);
		// ======== End: BOTTOM PANEL ========

		getContentPane().setLayout(
			new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		getContentPane().add(topPanel);
		getContentPane().add(summaryPanel);
		getContentPane().add(bottomPanel);
		getContentPane().add(bottomPanel2);

		getRootPane().setDefaultButton(apply);

		table.getModel().addTableModelListener(this);

		pack();

		SwingTools.addAccelerator(cancel, (JComponent) getContentPane(), cancel
			.getActionListeners()[0], KeyEvent.VK_ESCAPE, 0);

		addCustomViewOptions();
	}

	protected static class IJ1Plugin {

		protected Object file;
		protected Method run;

		protected void run(final String arg) {
			try {
				run.invoke(file, arg);
			}
			catch (final Exception e) {
				UserInterface.get().handleException(e);
			}
		}

		protected static IJ1Plugin discover(final String className) {
			try {
				final IJ1Plugin instance = new IJ1Plugin();
				final Class<?> clazz =
					IJ1Plugin.class.getClassLoader().loadClass(className);
				instance.file = clazz.newInstance();
				instance.run = instance.file.getClass().getMethod("run", String.class);
				return instance;
			}
			catch (final Exception e) {
				return null;
			}
		}
	}

	@Override
	public void setVisible(final boolean visible) {
		super.setVisible(visible && !hidden);
		if (visible) {
			UserInterface.get().addWindow(this);
			apply.requestFocusInWindow();
		}
	}

	@Override
	public void dispose() {
		UserInterface.get().removeWindow(this);
		super.dispose();
	}

	public Progress getProgress(final String title) {
		if (hidden) return new StderrProgress();
		return new ProgressDialog(this, title);
	}

	@Override
	public void valueChanged(final ListSelectionEvent event) {
		filesChanged();
	}

	List<FileAction> fileActions = new ArrayList<FileAction>();

	class FileAction extends JButton implements ActionListener {

		String label, otherLabel;
		Action action, otherAction;

		FileAction(final String label, final Action action) {
			this(label, action, null, null);
		}

		FileAction(final String label, final Action action,
			final String otherLabel, final Action otherAction)
		{
			super(label);
			this.label = label;
			this.action = action;
			this.otherLabel = otherLabel;
			this.otherAction = otherAction;
			addActionListener(this);
			fileActions.add(this);
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			if (table.isEditing()) table.editingCanceled(null);
			for (final FileObject file : table.getSelectedFiles()) {
				if (action == null) file.setNoAction();
				else if (!setAction(file)) continue;
				table.fireFileChanged(file);
			}
			filesChanged();
		}

		protected boolean setAction(final FileObject file) {
			return file.setFirstValidAction(files,
				new Action[] { action, otherAction });
		}

		public void enableIfValid() {
			boolean enable = false, enableOther = false;

			for (final FileObject file : table.getSelectedFiles()) {
				final Status status = file.getStatus();
				if (action == null) enable = true;
				else if (status.isValid(action)) enable = true;
				else if (status.isValid(otherAction)) enableOther = true;
				if (enable && enableOther) break;
			}
			setText(!enableOther ? label : (enable ? label + "/" : "") + otherLabel);
			setEnabled(enable || enableOther);
		}
	}

	public void addCustomViewOptions() {
		viewOptions.clearCustomOptions();

		final Collection<String> names = files.getUpdateSiteNames();
		if (names.size() > 1) for (final String name : names)
			viewOptions.addCustomOption("View files of the '" + name + "' site",
				files.forUpdateSite(name));
	}

	public void setViewOption(final ViewOptions.Option option) {
		viewOptions.setSelectedItem(option);
		updateFilesTable();
	}

	public void updateFilesTable() {
		Iterable<FileObject> view = viewOptions.getView(table);
		final Set<FileObject> selected = new HashSet<FileObject>();
		for (final FileObject file : table.getSelectedFiles())
			selected.add(file);
		table.clearSelection();

		final String search = txtSearch.getText().trim();
		if (!search.equals("")) view = FilesCollection.filter(search, view);

		// Directly update the table for display
		table.setFiles(view);
		for (int i = 0; i < table.getRowCount(); i++)
			if (selected.contains(table.getFile(i))) table.addRowSelectionInterval(i,
				i);
	}

	public void applyChanges() {
		final ResolveDependencies resolver = new ResolveDependencies(this, files);
		if (!resolver.resolve()) return;
		new Thread() {

			@Override
			public void run() {
				install();
			}
		}.start();
	}

	private void quit() {
		if (files.hasChanges()) {
			if (!SwingTools.showQuestion(hidden, this, "Quit?",
				"You have specified changes. Are you sure you want to quit?")) return;
		}
		else try {
			files.write();
		}
		catch (final Exception e) {
			error("There was an error writing the local metadata cache: " + e);
		}
		dispose();
	}

	void setEasyMode(final Container container) {
		for (final Component child : container.getComponents()) {
			if ((child instanceof Container) &&
				child != table.getParent().getParent()) setEasyMode((Container) child);
			if (child == upload && !easyMode && !files.hasUploadableSites()) child
				.setVisible(false);
			else child.setVisible(!easyMode);
		}
	}

	public void setEasyMode(final boolean easyMode) {
		this.easyMode = easyMode;
		setEasyMode(getContentPane());
		final Component[] exempt = { table, easy, apply, cancel };
		for (Component child : exempt)
			for (; child != getContentPane(); child = child.getParent())
				child.setVisible(true);
		easy.setText(easyMode ? "Advanced mode" : "Easy mode");
		if (isVisible()) repaint();
	}

	public void toggleEasyMode() {
		setEasyMode(!easyMode);
	}

	public void install() {
		final Installer installer =
			new Installer(files, getProgress("Installing..."));
		try {
			final FilesCollection uninstalled =
				FilesCollection.clone(files.toUninstall());
			installer.start();
			for (final FileObject file : uninstalled)
				if (!file.isFiji()) files.remove(file);
				else file.setStatus(file.isObsolete() ? Status.OBSOLETE_UNINSTALLED
					: Status.NOT_INSTALLED);
			updateFilesTable();
			filesChanged();
			files.write();
			info("Updated successfully.  Please restart ImageJ!");
			dispose();
		}
		catch (final Canceled e) {
			// TODO: remove "update/" directory
			error("Canceled");
			installer.done();
		}
		catch (final Exception e) {
			e.printStackTrace();
			// TODO: remove "update/" directory
			error("Installer failed: " + e);
			installer.done();
		}
	}

	public void updateTheUpdater() {
		final FilesCollection.Filter filter = new FilesCollection.Filter() {

			@Override
			public boolean matches(final FileObject file) {
				if (file.filename.equals("jars/ij-updater-core.jar")) {
					file.setAction(files, Action.UPDATE);
					return true;
				}
				return false;
			}
		};
		final FilesCollection justTheUpdater =
			FilesCollection.clone(files.filter(filter));
		justTheUpdater.cloneUpdateSites(files);
		final Installer installer =
			new Installer(justTheUpdater, getProgress("Installing the updater..."));
		try {
			installer.start();
		}
		catch (final Canceled e) {
			// TODO: remove "update/" directory
			error("Canceled");
			installer.done();
		}
		catch (final IOException e) {
			// TODO: remove "update/" directory
			error("Installer failed: " + e);
			installer.done();
		}
	}

	private Thread filesChangedWorker;

	public synchronized void filesChanged() {
		if (filesChangedWorker != null) return;

		filesChangedWorker = new Thread() {

			@Override
			public void run() {
				filesChangedWorker();
				synchronized (UpdaterFrame.this) {
					filesChangedWorker = null;
				}
			}
		};
		SwingUtilities.invokeLater(filesChangedWorker);
	}

	private void filesChangedWorker() {
		// TODO: once this is editable, make sure changes are committed
		fileDetails.reset();
		for (final FileObject file : table.getSelectedFiles())
			fileDetails.showFileDetails(file);
		if (fileDetails.getDocument().getLength() > 0 &&
			table.areAllSelectedFilesUploadable()) fileDetails
			.setEditableForDevelopers();

		for (final FileAction button : fileActions)
			button.enableIfValid();

		apply.setEnabled(files.hasChanges());
		cancel.setText(files.hasChanges() ? "Cancel" : "Close");

		if (files.hasUploadableSites()) enableUploadOrNot();

		int install = 0, uninstall = 0, upload = 0;
		long bytesToDownload = 0, bytesToUpload = 0;

		for (final FileObject file : files)
			switch (file.getAction()) {
				case INSTALL:
				case UPDATE:
					install++;
					bytesToDownload += file.filesize;
					break;
				case UNINSTALL:
					uninstall++;
					break;
				case UPLOAD:
					upload++;
					bytesToUpload += file.filesize;
					break;
			}
		int implicated = 0;
		final DependencyMap map = files.getDependencies(true);
		for (final FileObject file : map.keySet()) {
			implicated++;
			bytesToUpload += file.filesize;
		}
		String text = "";
		if (install > 0) text +=
			" install/update: " + install + (implicated > 0 ? "+" + implicated : "") +
				" (" + sizeToString(bytesToDownload) + ")";
		if (uninstall > 0) text += " uninstall: " + uninstall;
		if (files.hasUploadableSites() && upload > 0) text +=
			" upload: " + upload + " (" + sizeToString(bytesToUpload) + ")";
		fileSummary.setText(text);

	}

	protected final static String[] units = { "B", "kB", "MB", "GB", "TB" };

	public static String sizeToString(long size) {
		int i;
		for (i = 1; i < units.length && size >= 1l << (10 * i); i++); // do nothing
		if (--i == 0) return "" + size + units[i];
		// round
		size *= 100;
		size >>= (10 * i);
		size += 5;
		size /= 10;
		return "" + (size / 10) + "." + (size % 10) + units[i];
	}

	@Override
	public void tableChanged(final TableModelEvent e) {
		filesChanged();
	}

	// checkWritable() is guaranteed to be called after Checksummer ran
	public void checkWritable() {
		String list = null;
		for (final FileObject object : files) {
			final File file = new File(Util.prefix(object.getFilename()));
			if (!file.exists() || file.canWrite()) continue;
			if (list == null) list = object.getFilename();
			else list += ", " + object.getFilename();
		}
		if (list != null) UserInterface.get().info(
			"WARNING: The following files are set to read-only: '" + list + "'",
			"Read-only files");
	}

	void markUploadable() {
		canUpload = true;
		enableUploadOrNot();
	}

	void enableUploadOrNot() {
		upload.setVisible(!easyMode && files.hasUploadableSites());
		upload.setEnabled(canUpload || files.hasUploadOrRemove());
	}

	protected void upload() throws InstantiationException {
		final ResolveDependencies resolver =
			new ResolveDependencies(this, files, true);
		if (!resolver.resolve()) return;

		final String errors = files.checkConsistency();
		if (errors != null) {
			error(errors);
			return;
		}

		final List<String> possibleSites =
			new ArrayList<String>(files.getSiteNamesToUpload());
		if (possibleSites.size() == 0) {
			error("Huh? No upload site?");
			return;
		}
		String updateSiteName;
		if (possibleSites.size() == 1) updateSiteName = possibleSites.get(0);
		else {
			updateSiteName =
				SwingTools.getChoice(hidden, this, possibleSites,
					"Which site do you want to upload to?", "Update site");
			if (updateSiteName == null) return;
		}
		final FilesUploader uploader = new FilesUploader(files, updateSiteName);

		Progress progress = null;
		try {
			if (!uploader.login()) return;
			progress = getProgress("Uploading...");
			uploader.upload(progress);
			for (final FileObject file : files.toUploadOrRemove())
				if (file.getAction() == Action.UPLOAD) {
					file.markUploaded();
					file.setStatus(Status.INSTALLED);
				}
				else {
					file.markRemoved();
					file.setStatus(Status.OBSOLETE_UNINSTALLED);
				}
			updateFilesTable();
			canUpload = false;
			files.write();
			info("Uploaded successfully.");
			enableUploadOrNot();
			dispose();
		}
		catch (final Canceled e) {
			// TODO: teach uploader to remove the lock file
			error("Canceled");
			if (progress != null) progress.done();
		}
		catch (final Throwable e) {
			UserInterface.get().handleException(e);
			error("Upload failed: " + e);
			if (progress != null) progress.done();
		}
	}

	protected boolean initializeUpdateSite(final String url,
		final String sshHost, final String uploadDirectory)
		throws InstantiationException
	{
		final String updateSiteName = "Dummy";
		final FilesCollection files = new FilesCollection();
		files.addUpdateSite(updateSiteName, url, sshHost, uploadDirectory, Long
			.parseLong(Util.timestamp(-1)));
		final FilesUploader uploader = new FilesUploader(files, updateSiteName);
		Progress progress = null;
		try {
			if (!uploader.login()) return false;
			progress = getProgress("Initializing Update Site...");
			uploader.upload(progress);
			// JSch needs some time to finalize the SSH connection
			try {
				Thread.sleep(1000);
			}
			catch (final InterruptedException e) { /* ignore */}
			return true;
		}
		catch (final Canceled e) {
			if (progress != null) progress.done();
		}
		catch (final Throwable e) {
			UserInterface.get().handleException(e);
			if (progress != null) progress.done();
		}
		return false;
	}

	public void error(final String message) {
		SwingTools.showMessageBox(hidden, this, message, JOptionPane.ERROR_MESSAGE);
	}

	public void warn(final String message) {
		SwingTools.showMessageBox(hidden, this, message,
			JOptionPane.WARNING_MESSAGE);
	}

	public void info(final String message) {
		SwingTools.showMessageBox(hidden, this, message,
			JOptionPane.INFORMATION_MESSAGE);
	}
}
