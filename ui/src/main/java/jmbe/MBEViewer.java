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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Viewer for MBE file contents.
 */
public class MBEViewer extends VBox
{
    private static final String KEY_LAST_DIRECTORY = "mbe.last.directory";
    private final Preferences mPreferences = Preferences.userNodeForPackage(MBEViewer.class);
    private MenuBar mMenuBar;
    private GridPane mHeader;
    private TextField mFilePath;
    private TextField mProtocol;
    private TextField mCallType;
    private TextField mFrom;
    private TextField mTo;
    private TextField mSystem;
    private TextField mSite;

    public MBEViewer()
    {
        getChildren().addAll(getMenuBar(), getHeader());
    }

    /**
     * Loads the MBE file into the viewer.
     * @param path for the file
     */
    private void load(Path path)
    {
        if(path != null)
        {
            MBECallSequence sequence = MBECallSequenceReader.load(path);

            if(sequence != null)
            {
                getFilePath().setText(path.toString());
                getProtocol().setText(sequence.getProtocol());
                getCallType().setText(sequence.getCallType());
                getFrom().setText(sequence.getFromIdentifier());
                getTo().setText(sequence.getToIdentifier());
                getSystem().setText(sequence.getSystem());
                getSite().setText(sequence.getSite());
            }
        }
    }

    public GridPane getHeader()
    {
        if(mHeader == null)
        {
            mHeader = new GridPane();
            mHeader.setPadding(new Insets(5));
            mHeader.setHgap(5);
            mHeader.setVgap(5);

            int row = 0;
            Label fileLabel = new Label("MBE File:");
            GridPane.setHalignment(fileLabel, HPos.RIGHT);
            mHeader.add(fileLabel, 0, row);
            GridPane.setHgrow(getFilePath(), Priority.ALWAYS);
            mHeader.add(getFilePath(), 1, row, 5, 1);

            row++;

            Label callTypeLabel = new Label("Call Type:");
            GridPane.setHalignment(callTypeLabel, HPos.RIGHT);
            mHeader.add(callTypeLabel, 0, row);
            GridPane.setHgrow(getCallType(), Priority.ALWAYS);
            mHeader.add(getCallType(), 1, row);

            Label fromLabel = new Label("From:");
            GridPane.setHalignment(fromLabel, HPos.RIGHT);
            mHeader.add(fromLabel, 2, row);
            GridPane.setHgrow(getFrom(), Priority.ALWAYS);
            mHeader.add(getFrom(), 3, row);

            Label toLabel = new Label("To:");
            GridPane.setHalignment(toLabel, HPos.RIGHT);
            mHeader.add(toLabel, 4, row);
            GridPane.setHgrow(getTo(), Priority.ALWAYS);
            mHeader.add(getTo(), 5, row);

            row++;

            Label protocolLabel = new Label("Protocol:");
            GridPane.setHalignment(protocolLabel, HPos.RIGHT);
            mHeader.add(protocolLabel, 0, row);
            GridPane.setHgrow(getProtocol(), Priority.ALWAYS);
            mHeader.add(getProtocol(), 1, row);

            row++;

            Label systemLabel = new Label("System:");
            GridPane.setHalignment(systemLabel, HPos.RIGHT);
            mHeader.add(systemLabel, 0, row);
            GridPane.setHgrow(getSystem(), Priority.ALWAYS);
            mHeader.add(getSystem(), 1, row, 5, 1);

            row++;

            Label siteLabel = new Label("Site:");
            GridPane.setHalignment(siteLabel, HPos.RIGHT);
            mHeader.add(siteLabel, 0, row);
            GridPane.setHgrow(getSite(), Priority.ALWAYS);
            mHeader.add(getSite(), 1, row, 5, 1);
        }

        return mHeader;
    }

    /**
     * MBE file path
     */
    public TextField getFilePath()
    {
        if(mFilePath == null)
        {
            mFilePath = new TextField();
            mFilePath.setEditable(false);
        }

        return mFilePath;
    }

    /**
     * Radio protocol that generated the MBE file.
     */
    public TextField getProtocol()
    {
        if(mProtocol == null)
        {
            mProtocol = new TextField();
            mProtocol.setEditable(false);
        }

        return mProtocol;
    }

    /**
     * Call type
     */
    public TextField getCallType()
    {
        if(mCallType == null)
        {
            mCallType = new TextField();
            mCallType.setEditable(false);
        }

        return mCallType;
    }

    public TextField getFrom()
    {
        if(mFrom == null)
        {
            mFrom = new TextField();
            mFrom.setEditable(false);
        }

        return mFrom;
    }

    public TextField getTo()
    {
        if(mTo == null)
        {
            mTo = new TextField();
            mTo.setEditable(false);
        }

        return mTo;
    }

    public TextField getSystem()
    {
        if(mSystem == null)
        {
            mSystem = new TextField();
            mSystem.setEditable(false);
        }

        return mSystem;
    }

    public TextField getSite()
    {
        if(mSite == null)
        {
            mSite = new TextField();
            mSite.setEditable(false);
        }

        return mSite;
    }

    public MenuBar getMenuBar()
    {
        if(mMenuBar == null)
        {
            mMenuBar = new MenuBar();

            Menu fileMenu = new Menu("File");

            MenuItem openMenu = new MenuItem("Open");
            openMenu.onActionProperty().set(actionEvent -> {

                FileChooser fileChooser = new FileChooser();
                FileChooser.ExtensionFilter mbeFilter = new FileChooser.ExtensionFilter("MBE Files", "*.mbe");
                FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*.*");
                fileChooser.getExtensionFilters().addAll(mbeFilter, allFilter);
                fileChooser.setSelectedExtensionFilter(mbeFilter);
                fileChooser.setTitle("Select MBE File");

                String lastDirectory = mPreferences.get(KEY_LAST_DIRECTORY, null);
                if(lastDirectory != null)
                {
                    Path path = Paths.get(lastDirectory);
                    fileChooser.setInitialDirectory(path.toFile());
                }

                File selected = fileChooser.showOpenDialog(getScene().getWindow());

                if(selected != null)
                {
                    load(selected.toPath());
                    mPreferences.put(KEY_LAST_DIRECTORY, selected.getParent());
                }
            });

            fileMenu.getItems().addAll(openMenu);

            mMenuBar.getMenus().addAll(fileMenu);
        }

        return mMenuBar;
    }
}
