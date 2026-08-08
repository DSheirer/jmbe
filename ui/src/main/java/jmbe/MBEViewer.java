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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import jmbe.audio.JMBEAudioFormat;
import jmbe.codec.MBESynthesizer;
import jmbe.codec.imbe.IMBEFrame;
import jmbe.codec.imbe.IMBEFundamentalFrequency;
import jmbe.codec.imbe.IMBEModelParameters;
import jmbe.codec.imbe.IMBESynthesizer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.JOptionPane;

/**
 * Viewer for MBE file contents.
 */
public class MBEViewer extends VBox
{
    private static final String KEY_LAST_DIRECTORY = "mbe.last.directory";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final Preferences mPreferences = Preferences.userNodeForPackage(MBEViewer.class);
    private ObservableList<ObservableFrame> mFrames = FXCollections.observableArrayList();
    private MenuBar mMenuBar;
    private GridPane mHeader;
    private TableView<ObservableFrame> mFrameTable;
    private TextField mFilePath;
    private TextField mProtocol;
    private TextField mCallType;
    private TextField mFrom;
    private TextField mTo;
    private TextField mSystem;
    private TextField mSite;
    private Button mSynthesizeButton;

    public MBEViewer()
    {
        getChildren().addAll(getMenuBar(), getHeader(), getFrameTable());
        VBox.setVgrow(getFrameTable(), Priority.ALWAYS);
    }

    /**
     * Loads the MBE file into the viewer.
     * @param path for the file
     */
    private void load(Path path)
    {
        if(path != null)
        {
            mFrames.clear();

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

                IMBEModelParameters parameters = new IMBEModelParameters();

                for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
                {
                    IMBEFrame frame = new IMBEFrame(voiceFrame.getFrameBytes());
                    parameters = frame.getModelParameters(parameters);
                    mFrames.add(new ObservableFrame(voiceFrame, parameters));
                }
            }
        }
    }

    public Button getSynthesizeButton()
    {
        if(mSynthesizeButton == null)
        {
            mSynthesizeButton = new Button("Synthesize");
            mSynthesizeButton.setOnAction(ae -> synthesize());
        }

        return mSynthesizeButton;
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

            mHeader.add(getSynthesizeButton(), 3, row);

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
     * Table containing the voice frames from the loaded MBE file.
     */
    public TableView<ObservableFrame> getFrameTable()
    {
        if(mFrameTable == null)
        {
            mFrameTable = new TableView<>(mFrames);
//            mFrameTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            TableColumn<ObservableFrame, Void> frameNumberColumn = new TableColumn<>("#");
            frameNumberColumn.setSortable(false);
            frameNumberColumn.setPrefWidth(45);
            frameNumberColumn.setCellFactory(column -> new TableCell<>()
            {
                @Override
                protected void updateItem(Void item, boolean empty)
                {
                    super.updateItem(item, empty);
                    setText(empty ? null : Integer.toString(getIndex() + 1));
                }
            });

            TableColumn<ObservableFrame, String> timestampColumn = new TableColumn<>("Timestamp");
            timestampColumn.setPrefWidth(95);
            timestampColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(TIMESTAMP_FORMATTER.format(
                    Instant.ofEpochMilli(cell.getValue().getTimestamp()))));

            TableColumn<ObservableFrame, Boolean> encryptedColumn = new TableColumn<>("Enc");
            encryptedColumn.setPrefWidth(45);
            encryptedColumn.setCellValueFactory(new PropertyValueFactory<>("encrypted"));

            TableColumn<ObservableFrame, IMBEFundamentalFrequency> frequencyColumn =
                new TableColumn<>("Frequency");
            frequencyColumn.setCellValueFactory(new PropertyValueFactory<>("fundamentalFrequency"));

            TableColumn<ObservableFrame, Integer> bandCountColumn = new TableColumn<>("Bands");
            bandCountColumn.setCellValueFactory(new PropertyValueFactory<>("bandCount"));

            TableColumn<ObservableFrame, String> errorRateColumn = new TableColumn<>("Error Rate");
            errorRateColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                String.format(Locale.ROOT, "%.5f", cell.getValue().getErrorRate())));

            TableColumn<ObservableFrame, Integer> errorTotalColumn = new TableColumn<>("Error Total");
            errorTotalColumn.setCellValueFactory(new PropertyValueFactory<>("errorCountTotal"));

            mFrameTable.getColumns().addAll(frameNumberColumn, timestampColumn, encryptedColumn,
                frequencyColumn, bandCountColumn, errorRateColumn, errorTotalColumn);
        }

        return mFrameTable;
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

    private void synthesize()
    {
        if(getFilePath().getText().isEmpty())
        {
            return;
        }

        MBECallSequence sequence = MBECallSequenceReader.load(Path.of(getFilePath().getText()));

        if(sequence == null)
        {
            return;
        }

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                List<float[]> samplesList = new ArrayList<>();
                int sampleCount = 0;

                //Invoke the synthesize for IMBE audio
                IMBESynthesizer synthesizer = new IMBESynthesizer();

                for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
                {
                    float[] samples = synthesizer.getAudio(new IMBEFrame(voiceFrame.getFrameBytes()));
                    sampleCount += samples.length;
                    samplesList.add(samples);
                }

                AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        8000.0f, 16, 1, 2, 8000.0f, false);
                ByteBuffer pcm = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN);

                for(float[] samples: samplesList)
                {
                    for(float sample : samples)
                    {
                        float clipped = Math.max(-0.95f, Math.min(0.95f, 5 * sample));
                        pcm.putShort((short)(clipped * Short.MAX_VALUE));
                    }
                }

                byte[] audioBytes = pcm.array();
                AudioInputStream audioInputStream = new AudioInputStream(
                        new ByteArrayInputStream(audioBytes), audioFormat, sampleCount);

                File outputFile = Paths.get("/run/media/denny/T9/Recordings/AMBE Research/mbe_output.wav").toFile();

                try
                {
                    AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
                }
                catch(IOException ioe)
                {
                    ioe.printStackTrace();
                }

                JOptionPane.showMessageDialog(null, "Synthesized audio saved to " + outputFile.getAbsolutePath());
            }
        }).start();
    }

    public static void main(String[] args) throws IOException
    {
        float[] samples = new float[8000];

        for(int x = 0; x < samples.length; x++)
        {
            samples[x] = (float)Math.sin(2.0 * Math.PI * 440.0 * x / 8000.0);
        }

        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        ByteBuffer pcm = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);

        for(float sample : samples)
        {
            float clipped = Math.max(-1.0f, Math.min(1.0f, sample));
            pcm.putShort((short)(clipped * Short.MAX_VALUE));
        }

        byte[] audioBytes = pcm.array();
        AudioInputStream audioInputStream = new AudioInputStream(
            new ByteArrayInputStream(audioBytes), audioFormat, samples.length);

        File outputFile = Paths.get("/run/media/denny/T9/Recordings/AMBE Research/mbe_output.wav").toFile();
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);

    }
}
