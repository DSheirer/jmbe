/*
 * **********************************************************
 * jmbe - Java MBE Library
 * Copyright (C) 2014-2017 Dennis Sheirer
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
 * **********************************************************
 */

package builder;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public class JMBEBuilder extends Application
{
    private final static Logger mLog = LoggerFactory.getLogger(JMBEBuilder.class);

    private Stage mStage;
    private Scene mScene;
    private BorderPane mParentLayout;
    private VBox mCenterLayout;
    private Label mInformationLabel;
    private HBox mJDKLayout;
    private Button mJDKSelectButton;
    private Label mJDKPathLabel;
    private HBox mDownloadLayout;
    private Label mDownloadLabel;
    private ProgressIndicator mDownloadProgressIndicator;
    private HBox mCompileLayout;
    private Label mCompileLabel;
    private ProgressIndicator mCompileProgressIndicator;
    private Label mStatusLabel;
    private Button mStartButton;

    private JDKManager mJDKManager;

    /**
     * GUI builder for the JMBE Audio Library.
     */
    public JMBEBuilder()
    {
    }

    @Override
    public void start(Stage primaryStage) throws Exception
    {
        mLog.info("Java MBE Audio Library Builder");

        mJDKManager = new JDKManager();

        mStage = primaryStage;
        mStage.setTitle("JMBE Audio Library Builder");
        mStage.setScene(getScene());
        mStage.show();
    }

    private Stage getStage()
    {
        return mStage;
    }

    /**
     * Scene for the primary stage
     */
    private Scene getScene()
    {
        if(mScene == null)
        {
            mScene = new Scene(getParentLayout(), 300, 300);

            updateStatus();
        }

        return mScene;
    }

    /**
     * Top level layout pane for the gui
     */
    private BorderPane getParentLayout()
    {
        if(mParentLayout == null)
        {
            mParentLayout = new BorderPane();
            mParentLayout.setCenter(getCenterLayout());

            BorderPane.setAlignment(getStartButton(), Pos.BOTTOM_CENTER);
            BorderPane.setMargin(getStartButton(), new Insets(10.0,10.0,10.0,10.0));
            mParentLayout.setBottom(getStartButton());
        }

        return mParentLayout;
    }

    /**
     * Center pane layout containing instructions and progress bars.
     */
    private VBox getCenterLayout()
    {
        if(mCenterLayout == null)
        {
            mCenterLayout = new VBox();


            VBox.setMargin(getInformationLabel(), new Insets(10.0,10.0,0.0,10.0));
            mCenterLayout.getChildren().add(getInformationLabel());

            VBox.setMargin(getJDKLayout(), new Insets(10.0,10.0,0.0,10.0));
            mCenterLayout.getChildren().add(getJDKLayout());

            VBox.setMargin(getDownloadLayout(), new Insets(10.0,10.0,10.0,10.0));
            mCenterLayout.getChildren().add(getDownloadLayout());

            VBox.setMargin(getCompileLayout(), new Insets(10.0,10.0,10.0,10.0));
            mCenterLayout.getChildren().add(getCompileLayout());

            VBox.setMargin(getStatusLabel(), new Insets(10.0,10.0,0.0,10.0));
            mCenterLayout.getChildren().add(getStatusLabel());
        }

        return mCenterLayout;
    }

    /**
     * Information and instructions label
     */
    private Label getInformationLabel()
    {
        if(mInformationLabel == null)
        {
            String instructions = "This application downloads and compiles the JMBE audio library.";

            mInformationLabel = new Label();
            mInformationLabel.setText(instructions);
            mInformationLabel.setMaxWidth(Double.MAX_VALUE);
            mInformationLabel.setWrapText(true);
        }

        return mInformationLabel;
    }

    private HBox getJDKLayout()
    {
        if(mJDKLayout == null)
        {
            mJDKLayout = new HBox();
            HBox.setMargin(getJDKPathLabel(), new Insets(0.0, 0.0, 0.0, 10.0));
            mJDKLayout.getChildren().addAll(getJDKSelectButton(), getJDKPathLabel());
        }

        return mJDKLayout;
    }

    /**
     * Button to open JDK file selector
     * @return
     */
    private Button getJDKSelectButton()
    {
        if(mJDKSelectButton == null)
        {
            mJDKSelectButton = new Button("Select JDK");
            mJDKSelectButton.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    DirectoryChooser directoryChooser = new DirectoryChooser();

                    if(mJDKManager.hasJavaHome())
                    {
                        directoryChooser.setInitialDirectory(mJDKManager.getJavaHome().toFile());
                    }

                    directoryChooser.setTitle("Please select an installed Java JDK directory");
                    File selectedDirectory = directoryChooser.showDialog(getStage());

                    if(selectedDirectory != null)
                    {
                        Path validatedJavaHome = JDKManager.validateJavaHome(selectedDirectory.toPath());

                        if(validatedJavaHome != null)
                        {
                            mJDKManager.setJavaHome(validatedJavaHome);

                            getJDKPathLabel().setText(validatedJavaHome.toString());
                        }
                        else if(selectedDirectory != null)
                        {
                            String directoryText = "Invalid directory [" + selectedDirectory.toString() + "]";
                            String alertText = "Directory must contain a sub-folder 'bin' and an executable " +
                                "program 'javac' in the bin folder";

                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setHeaderText(directoryText);
                            alert.setTitle("Invalid Java JDK Directory");

                            Label alertLabel = new Label(alertText);
                            alertLabel.setWrapText(true);
                            alert.getDialogPane().setContent(alertLabel);
                            alert.show();
                        }
                    }

                    updateStatus();
                }
            });
        }

        return mJDKSelectButton;
    }

    private Label getJDKPathLabel()
    {
        if(mJDKPathLabel == null)
        {
            mJDKPathLabel = new Label("");

            if(mJDKManager.getJavaHome() != null)
            {
                mJDKPathLabel.setText(mJDKManager.getJavaHome().toString());
            }
        }

        return mJDKPathLabel;
    }

    /**
     * Updates the status label and and enables/disables the start button
     */
    private void updateStatus()
    {
        if(mJDKManager.hasJavaHome())
        {
            getStartButton().setDisable(false);
            getStatusLabel().setText("Click 'Start' button to begin.");
        }
        else
        {
            getStartButton().setDisable(true);
            getStatusLabel().setText("Please select an installed Java JDK.");
        }
    }

    /**
     * Layout for the download components
     */
    private HBox getDownloadLayout()
    {
        if(mDownloadLayout == null)
        {
            mDownloadLayout = new HBox();
            mDownloadLayout.setAlignment(Pos.CENTER_LEFT);

            HBox.setMargin(getDownloadLabel(), new Insets(0.0, 0.0, 0.0, 10.0));

            mDownloadLayout.getChildren().addAll(getDownloadProgressIndicator(), getDownloadLabel());
        }

        return mDownloadLayout;
    }

    /**
     * Label for the download progress bar
     */
    private Label getDownloadLabel()
    {
        if(mDownloadLabel == null)
        {
            mDownloadLabel = new Label("Download Source Code");
        }

        return mDownloadLabel;
    }

    /**
     * Download source code progress bar
     */
    private ProgressIndicator getDownloadProgressIndicator()
    {
        if(mDownloadProgressIndicator == null)
        {
            mDownloadProgressIndicator = new ProgressIndicator(0.0);
            mDownloadProgressIndicator.setMaxWidth(Double.MAX_VALUE);
        }

        return mDownloadProgressIndicator;
    }

    /**
     * Layout for the compile components
     */
    private HBox getCompileLayout()
    {
        if(mCompileLayout == null)
        {
            mCompileLayout = new HBox();
            HBox.setMargin(getCompileLabel(), new Insets(0.0, 0.0, 0.0, 10.0));
            mCompileLayout.getChildren().addAll(getCompileProgressIndicator(), getCompileLabel());
            getCompileLabel().setAlignment(Pos.CENTER_LEFT);
        }

        return mCompileLayout;
    }

    /**
     * Label for the compile progress bar
     */
    private Label getCompileLabel()
    {
        if(mCompileLabel == null)
        {
            mCompileLabel = new Label("Compile JMBE Library");
        }

        return mCompileLabel;
    }

    /**
     * Compile source code progress bar
     */
    private ProgressIndicator getCompileProgressIndicator()
    {
        if(mCompileProgressIndicator == null)
        {
            mCompileProgressIndicator = new ProgressIndicator(0.0);
            mCompileProgressIndicator.setMaxWidth(Double.MAX_VALUE);
        }

        return mCompileProgressIndicator;
    }

    private Label getStatusLabel()
    {
        if(mStatusLabel == null)
        {
            mStatusLabel = new Label();
            mStatusLabel.setWrapText(true);
        }

        return mStatusLabel;
    }

    /**
     * Button to start downloading and compiling the JMBE library
     */
    private Button getStartButton()
    {
        if(mStartButton == null)
        {
            mStartButton = new Button("Start");

            mStartButton.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    downloadSourceCode();
                }
            });
        }

        return mStartButton;
    }

    /**
     * Creates the GIT repository manager and checks out or refreshes the local source code master branch
     */
    private void downloadSourceCode()
    {
        getDownloadProgressIndicator().setProgress(-1.0);

        final GITRepositoryManager repositoryManager = new GITRepositoryManager();

        final Task<Void> downloadTask = new Task<Void>()
        {
            @Override
            protected Void call() throws Exception
            {
                repositoryManager.init();
                return null;
            }
        };

        downloadTask.stateProperty().addListener(new ChangeListener<Worker.State>()
        {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue)
            {
                if(newValue == Worker.State.SUCCEEDED)
                {
                    getDownloadProgressIndicator().setProgress(1.0);
                    compileSourceCode(repositoryManager.getRepositoryDirectory(), mJDKManager.getJavaHome());
                }
                else if(newValue == Worker.State.FAILED)
                {
                    mLog.info("State:" + newValue + " - error initializing and updating local GIT repository", downloadTask.getException());
                }
            }
        });

        Executors.newSingleThreadExecutor().submit(downloadTask);
    }

    private void compileSourceCode(Path repositoryDirectory, Path javaHome)
    {
        mLog.info("Compiling source code for repository: " + repositoryDirectory.toString());

        if(javaHome == null)
        {
            getStatusLabel().setText("Java home directory is invalid.");
        }



        getCompileProgressIndicator().setProgress(-1.0);

        AntExecutor antExecutor = new AntExecutor(repositoryDirectory, mJDKManager.getJavaHome());

        final Task<Void> compileTask = new Task<Void>()
        {
            @Override
            protected Void call() throws Exception
            {
                antExecutor.build();
                return null;
            }
        };

        compileTask.stateProperty().addListener(new ChangeListener<Worker.State>()
        {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue)
            {
                if(newValue == Worker.State.SUCCEEDED)
                {
                    getCompileProgressIndicator().setProgress(1.0);
                }
                else if(newValue == Worker.State.FAILED)
                {
                    mLog.info("Error compiling source code with ant", compileTask.getException());
                }
            }
        });

        Executors.newSingleThreadExecutor().submit(compileTask);
    }

    /**
     * Main method for launching the JMBE Builder GUI
     */
    public static void main(String[] args)
    {
        launch(args);
    }
}
