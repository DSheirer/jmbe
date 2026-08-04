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

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

public class JmbeViewer extends VBox
{
    private MenuBar mMenuBar;

    public JmbeViewer()
    {
        getChildren().add(getMenuBar());

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
                //TODO: store last directory
                File selected = fileChooser.showOpenDialog(getScene().getWindow());

                if(selected != null)
                {
                    System.out.println("Selected: " + selected.getAbsolutePath());
                }
            });

            fileMenu.getItems().addAll(openMenu);

            mMenuBar.getMenus().addAll(fileMenu);
        }

        return mMenuBar;
    }
}
