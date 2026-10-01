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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import jmbe.codec.FrameType;
import jmbe.codec.MBEModelParameters;
import jmbe.codec.ambe.AMBEFrame;
import jmbe.codec.ambe.AMBEModelParameters;
import jmbe.codec.ambe.AMBESynthesizer;
import jmbe.codec.imbe.IMBEFrame;
import jmbe.codec.imbe.IMBEModelParameters;
import jmbe.codec.imbe.IMBESynthesizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thumbdv.ThumbDv;
import thumbdv.util.WaveUtils;

/**
 * Viewer for MBE file contents.
 */
public class MBEViewer extends VBox
{
    private static final Logger LOG = LoggerFactory.getLogger(MBEViewer.class);
    private static final String KEY_LAST_DIRECTORY = "mbe.last.directory";
    private static final String KEY_LAST_FILE = "mbe.last.file";
    private static final List<String> AMBE_PROTOCOLS = List.of("NXDN", "DMR", "APCO25-PHASE2");
    private static final List<String> IMBE_PROTOCOLS = List.of("APCO25-PHASE1");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final DecimalFormat TIME_OFFSET_FORMAT = new DecimalFormat("0.00");
    private final Preferences mPreferences = Preferences.userNodeForPackage(MBEViewer.class);
    private final ObservableList<ObservableMBEFrame> mFrames = FXCollections.observableArrayList();
    private MenuBar mMenuBar;
    private GridPane mHeader;
    private TableView<ObservableMBEFrame> mFrameTable;
    private TextField mFilePath;
    private TextField mProtocol;
    private TextField mCallType;
    private TextField mFrom;
    private TextField mTo;
    private TextField mSystem;
    private TextField mSite;
    private Button mSynthesizeButton;
    private final XYChart.Series<Number,Number> mJmbeAmplitudeSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> mJmbeEnhancedAmplitudeSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> mAmbeAmplitudeSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> mAmbeAudioSampleSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> mAmbeAudioSampleSelectedSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> mJmbeAudioSampleSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> mJmbeAudioSampleSelectedSeries = new XYChart.Series<>();
    private LineChart<Number,Number> mAmplitudeLineChart;
    private LineChart<Number,Number> mAmbeAudioLineChart;
    private LineChart<Number,Number> mJmbeAudioLineChart;
    private CheckBox mShowJmbeCheckBox = new CheckBox("JMBE");
    private CheckBox mShowJmbeEnhancedCheckBox = new CheckBox("JMBE Enhanced");
    private CheckBox mShowAmbeCheckBox = new CheckBox("AMBE");
    private HBox mCheckBoxes;

    public MBEViewer()
    {
        VBox chartBox = new VBox();
        chartBox.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(getAmplitudeLineChart(), Priority.ALWAYS);
        chartBox.setSpacing(0);
        VBox.setVgrow(getCheckBoxes(), Priority.NEVER);
        chartBox.getChildren().addAll(getAmplitudeLineChart(), getCheckBoxes());

        VBox tableAndAudioBox = new VBox();
        VBox.setVgrow(getJmbeAudioLineChart(), Priority.NEVER);
        VBox.setVgrow(getAmbeAudioLineChart(), Priority.NEVER);
        VBox.setVgrow(getFrameTable(), Priority.ALWAYS);
        tableAndAudioBox.getChildren().addAll(getJmbeAudioLineChart(), getAmbeAudioLineChart(), getFrameTable());

        HBox hbox = new HBox();
        HBox.setHgrow(chartBox, Priority.ALWAYS);
        HBox.setHgrow(tableAndAudioBox, Priority.NEVER);
        hbox.getChildren().addAll(tableAndAudioBox, chartBox);
        VBox.setVgrow(hbox, Priority.ALWAYS);
        getChildren().addAll(getMenuBar(), getHeader(), hbox);

        //Auto load the last used file
        String lastDirectory = mPreferences.get(KEY_LAST_DIRECTORY, null);
        String lastFile = mPreferences.get(KEY_LAST_FILE, null);
        if(lastDirectory != null && lastFile != null)
        {
            Path path = Paths.get(lastDirectory, lastFile);

            if(Files.exists(path) && Files.isRegularFile(path))
            {
                load(path);
            }
        }
    }

    private HBox getCheckBoxes()
    {
        if(mCheckBoxes == null)
        {
            mShowAmbeCheckBox.setSelected(true);
            mShowJmbeCheckBox.setSelected(true);
            mShowJmbeEnhancedCheckBox.setSelected(true);
            mShowAmbeCheckBox.setOnAction(event -> mAmbeAmplitudeSeries.getNode().setVisible(mShowAmbeCheckBox.isSelected()));
            mShowJmbeCheckBox.setOnAction(event -> mJmbeAmplitudeSeries.getNode().setVisible(mShowJmbeCheckBox.isSelected()));
            mShowJmbeEnhancedCheckBox.setOnAction(event -> mJmbeEnhancedAmplitudeSeries.getNode().setVisible(mShowJmbeEnhancedCheckBox.isSelected()));
            mCheckBoxes = new HBox();
            mCheckBoxes.setSpacing(10);
            mCheckBoxes.setPadding(new Insets(10));
            mCheckBoxes.getChildren().addAll(mShowJmbeCheckBox, mShowJmbeEnhancedCheckBox, mShowAmbeCheckBox);
        }

        return mCheckBoxes;
    }

    private LineChart<Number,Number> getAmbeAudioLineChart()
    {
        if(mAmbeAudioLineChart == null)
        {
            NumberAxis valueAxis = new NumberAxis(-1, 1, .25);
            NumberAxis sampleAxis = new NumberAxis(0, 800, 40);
            mAmbeAudioLineChart = new LineChart<>(sampleAxis, valueAxis);
            mAmbeAudioLineChart.setAnimated(false);
            mAmbeAudioLineChart.setCreateSymbols(false);
            mAmbeAudioLineChart.setMaxWidth(Double.MAX_VALUE);
            mAmbeAudioLineChart.setPrefHeight(50);
            mAmbeAudioSampleSeries.setName("AMBE");
            mAmbeAudioLineChart.getData().add(mAmbeAudioSampleSeries);
            mAmbeAudioLineChart.getData().add(mAmbeAudioSampleSelectedSeries);
        }

        return mAmbeAudioLineChart;
    }

    private LineChart<Number,Number> getJmbeAudioLineChart()
    {
        if(mJmbeAudioLineChart == null)
        {
            NumberAxis valueAxis = new NumberAxis(-1, 1, .25);
            NumberAxis sampleAxis = new NumberAxis(0, 800, 40);
            mJmbeAudioLineChart = new LineChart<>(sampleAxis, valueAxis);
            mJmbeAudioLineChart.setAnimated(false);
            mJmbeAudioLineChart.setCreateSymbols(false);
            mJmbeAudioLineChart.setMaxWidth(Double.MAX_VALUE);
            mJmbeAudioLineChart.setPrefHeight(50);
            mJmbeAudioSampleSeries.setName("AMBE");
            mJmbeAudioLineChart.getData().add(mJmbeAudioSampleSeries);
            mJmbeAudioLineChart.getData().add(mJmbeAudioSampleSelectedSeries);
        }

        return mJmbeAudioLineChart;
    }


    private LineChart<Number,Number> getAmplitudeLineChart()
    {
        if(mAmplitudeLineChart == null)
        {
            NumberAxis frequencyAxis = new NumberAxis(0, 4000, 100);
            NumberAxis amplitudeAxis = new NumberAxis(-140, 0, 10);
            mAmplitudeLineChart = new LineChart<>(frequencyAxis, amplitudeAxis);
            mJmbeAmplitudeSeries.setName("JMBE");
            mAmplitudeLineChart.getData().add(mJmbeAmplitudeSeries);
            mJmbeEnhancedAmplitudeSeries.setName("JMBE-E");
            mAmplitudeLineChart.getData().add(mJmbeEnhancedAmplitudeSeries);
            mAmbeAmplitudeSeries.setName("AMBE");
            mAmplitudeLineChart.getData().add(mAmbeAmplitudeSeries);
            mAmplitudeLineChart.setAnimated(false);
        }

        return mAmplitudeLineChart;
    }

    private void updateCharts(ObservableMBEFrame selectedFrame)
    {
        mJmbeAmplitudeSeries.getData().clear();
        mJmbeEnhancedAmplitudeSeries.getData().clear();
        mAmbeAmplitudeSeries.getData().clear();
        mJmbeAudioSampleSeries.getData().clear();
        mAmbeAudioSampleSeries.getData().clear();
        mJmbeAudioSampleSelectedSeries.getData().clear();
        mAmbeAudioSampleSelectedSeries.getData().clear();

        if(selectedFrame.hasAmplitudes())
        {
            float fundamentalFrequency = selectedFrame.getFundamentalFrequency();
            double[] jmbeAmplitudes = selectedFrame.getJmbeAmplitudes();
            double[] jmbeEnhancedAmplitudes = selectedFrame.getJmbeEnhancedAmplitudes();
            double[] ambeAmplitudes = selectedFrame.getAmbeAmplitudes();
            boolean[] voicingDecisions = selectedFrame.getVoicingDecisions();

            double frequency;

            for(int i = 0; i < jmbeAmplitudes.length; i++)
            {
                frequency = (i + 1) * fundamentalFrequency;

                mAmbeAmplitudeSeries.getData().add(new XYChart.Data<>(frequency, ambeAmplitudes[i]));
                XYChart.Data<Number,Number> dataPoint = new XYChart.Data<>(frequency, jmbeAmplitudes[i]);

                //Voicing decisions array includes an unused 0 index value
                if(voicingDecisions[i + 1])
                {
                    Label voicingLabel = new Label("v");
                    voicingLabel.setTranslateY(-40);
                    dataPoint.setNode(voicingLabel);
                }

                mJmbeAmplitudeSeries.getData().add(dataPoint);
                mJmbeEnhancedAmplitudeSeries.getData().add(new XYChart.Data<>(frequency, jmbeEnhancedAmplitudes[i]));
            }

            int baseIndex = mFrames.indexOf(selectedFrame);
            float[] jmbeAudioPcm;
            float[] ambeAudioPcm;

            for(int offset = -2; offset <= 2; offset++)
            {
                int frameIndex = baseIndex + offset;

                if(offset == 0)
                {
                    jmbeAudioPcm = selectedFrame.getJmbeAudioPcm();
                    ambeAudioPcm = selectedFrame.getAmbeAudioPcm();

                    for(int i = 0; i < 160; i++)
                    {
                        mJmbeAudioSampleSelectedSeries.getData().add(new XYChart.Data<>(320 + i, jmbeAudioPcm[i]));
                        mAmbeAudioSampleSelectedSeries.getData().add(new XYChart.Data<>(320 + i, ambeAudioPcm[i]));
                    }
                }
                else
                {
                    if(frameIndex >= 0 && frameIndex < mFrames.size())
                    {
                        ObservableMBEFrame frame = mFrames.get(frameIndex);
                        jmbeAudioPcm = frame.getJmbeAudioPcm();
                        ambeAudioPcm = frame.getAmbeAudioPcm();

                        int sampleOffset = 160 * (offset + 2);
                        for(int i = 0; i < 160; i++)
                        {
                            mJmbeAudioSampleSeries.getData().add(new XYChart.Data<>(i + sampleOffset, jmbeAudioPcm[i]));
                            mAmbeAudioSampleSeries.getData().add(new XYChart.Data<>(i + sampleOffset, ambeAudioPcm[i]));
                        }
                    }
                }
            }

            mJmbeAmplitudeSeries.getNode().setStyle("-fx-stroke: #0000ff;");
            mJmbeEnhancedAmplitudeSeries.getNode().setStyle("-fx-stroke: #00ff00;");
            mAmbeAmplitudeSeries.getNode().setStyle("-fx-stroke: #ff0000;");
        }
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

                double timeOffset = 0.0;

                if(IMBE_PROTOCOLS.contains(sequence.getProtocol()))
                {
                    IMBEModelParameters parameters = new IMBEModelParameters();

                    for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
                    {
                        IMBEFrame frame = new IMBEFrame(voiceFrame.getFrameBytes());
                        parameters = frame.getModelParameters(parameters);
                        mFrames.add(new ObservableMBEFrame(voiceFrame, parameters, timeOffset));
                        timeOffset += 0.02;
                    }
                }
                else if(AMBE_PROTOCOLS.contains(sequence.getProtocol()))
                {
                    AMBEModelParameters parameters = new AMBEModelParameters();

                    for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
                    {
                        AMBEFrame frame = new AMBEFrame(voiceFrame.getFrameBytes());

                        if(frame.isToneFrame())
                        {
                            LOG.warn("Tone frame support not yet available");
//                            parameters = frame.getToneParameters();
                        }
                        else
                        {
                            parameters = frame.getVoiceParameters(parameters);
                        }

                        mFrames.add(new ObservableMBEFrame(voiceFrame, parameters, timeOffset));
                        timeOffset += 0.02;
                    }
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
    public TableView<ObservableMBEFrame> getFrameTable()
    {
        if(mFrameTable == null)
        {
            mFrameTable = new TableView<>(mFrames);
//            mFrameTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            TableColumn<ObservableMBEFrame, Void> frameNumberColumn = new TableColumn<>("#");
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

            TableColumn<ObservableMBEFrame, String> timestampColumn = new TableColumn<>("Timestamp");
            timestampColumn.setPrefWidth(95);
            timestampColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(TIMESTAMP_FORMATTER.format(
                    Instant.ofEpochMilli(cell.getValue().getTimestamp()))));

            TableColumn<ObservableMBEFrame, Double> timeOffsetColumn = new TableColumn<>("Offset");
            timeOffsetColumn.setCellValueFactory(new PropertyValueFactory<>("timeOffset"));

            TableColumn<ObservableMBEFrame, Boolean> encryptedColumn = new TableColumn<>("Enc");
            encryptedColumn.setPrefWidth(45);
            encryptedColumn.setCellValueFactory(new PropertyValueFactory<>("encrypted"));

            TableColumn<ObservableMBEFrame, String> fundamentalNameColumn = new TableColumn<>("FF");
            fundamentalNameColumn.setCellValueFactory(new PropertyValueFactory<>("fundamentalName"));

            TableColumn<ObservableMBEFrame, Float> frequencyColumn = new TableColumn<>("Frequency");
            frequencyColumn.setCellValueFactory(new PropertyValueFactory<>("fundamentalFrequency"));

            TableColumn<ObservableMBEFrame, Integer> bandCountColumn = new TableColumn<>("L");
            bandCountColumn.setCellValueFactory(new PropertyValueFactory<>("bandCount"));

            TableColumn<ObservableMBEFrame, String> errorRateColumn = new TableColumn<>("Err R");
            errorRateColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                String.format(Locale.ROOT, "%.5f", cell.getValue().getErrorRate())));

            TableColumn<ObservableMBEFrame, Integer> errorTotalColumn = new TableColumn<>("Err T");
            errorTotalColumn.setCellValueFactory(new PropertyValueFactory<>("errorCountTotal"));

            TableColumn<ObservableMBEFrame, Integer> frameRepeatCountColumn = new TableColumn<>("Repeats");
            frameRepeatCountColumn.setCellValueFactory(new PropertyValueFactory<>("repeatCount"));

            TableColumn<ObservableMBEFrame, String> voicingPercentageColumn = new TableColumn<>("V %");
            voicingPercentageColumn.setCellValueFactory(cell ->
                    new SimpleStringProperty(NumberFormat.getPercentInstance().format(cell.getValue().getVoicingPercentage())));

            mFrameTable.getColumns().addAll(frameNumberColumn, timestampColumn, timeOffsetColumn, encryptedColumn,
                fundamentalNameColumn, frequencyColumn, bandCountColumn, errorRateColumn, errorTotalColumn,
                    frameRepeatCountColumn, voicingPercentageColumn);

            mFrameTable.setRowFactory(tv -> {
                TableRow<ObservableMBEFrame> row = new TableRow<>();

                ContextMenu contextMenu = new ContextMenu();
                MenuItem viewDetails = new MenuItem("View Details");
                viewDetails.setOnAction((event) -> {
                   ObservableMBEFrame frame = row.getItem();
                   Alert alert = new Alert(Alert.AlertType.INFORMATION);
                   alert.setResizable(true);
                   alert.setTitle("MBE Frame Details");
                   alert.setHeaderText("Frame:" + (mFrames.indexOf(frame) + 1) + " Offset:" + TIME_OFFSET_FORMAT.format(frame.getTimeOffset()));
                   alert.setContentText(frame.getDescription());
                   alert.show();
                });
                contextMenu.getItems().add(viewDetails);

                row.emptyProperty().addListener((obs, prev, empty) -> {
                    row.setContextMenu(empty ? null : contextMenu);
                });

                return row;
            });

            mFrameTable.getSelectionModel().selectedItemProperty()
                    .addListener((obs, prev, empty) ->
                {
                    updateCharts(obs.getValue());
                });
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
                String lastFile = mPreferences.get(KEY_LAST_FILE, null);
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
                    mPreferences.put(KEY_LAST_FILE, selected.getName());
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

        new Thread(() -> {

            List<Float> fundamentalFrequencies = new ArrayList<>();
            List<Integer> harmonicCounts = new ArrayList<>();

            List<byte[]> jmbeAudioData;
            List<byte[]> jmbeAudioDataEnhanced;

            if(AMBE_PROTOCOLS.contains(sequence.getProtocol()))
            {
                jmbeAudioData = getAMBEAudio(sequence, false);
                jmbeAudioDataEnhanced = getAMBEAudio(sequence, true);
            }
            else if(IMBE_PROTOCOLS.contains(sequence.getProtocol()))
            {
                jmbeAudioData = getIMBEAudio(sequence, false);
                jmbeAudioDataEnhanced = getIMBEAudio(sequence, true);
            }
            else
            {
                jmbeAudioData = new ArrayList<>();
                jmbeAudioDataEnhanced = new ArrayList<>();
            }

            List<byte[]> ambeFrameData = new ArrayList<>();
            for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
            {
                ambeFrameData.add(voiceFrame.getFrameBytes());
            }

            List<byte[]> ambeAudioData = ThumbDv.decode(ambeFrameData, ThumbDv.AudioProtocol.DMR);
            Path stereoOutput = Paths.get("/run/media/denny/T9/AMBE Research/output_both.wav");
            Path monoJmbe = Paths.get("/run/media/denny/T9/AMBE Research/output_jmbe.wav");
            Path monoAmbe = Paths.get("/run/media/denny/T9/AMBE Research/output_ambe3000.wav");
            Path csvAnalysis = Paths.get("/run/media/denny/T9/AMBE Research/spectrum_analysis.csv");

            LOG.info("Audio collected - JMBE: " + jmbeAudioData.size() + " AMBE:" + ambeAudioData.size());

            while(ambeAudioData.size() < ambeFrameData.size())
            {
                int length = ambeAudioData.get(0).length;
                ambeAudioData.add(new byte[length]);
                LOG.warn("Adding new byte array length " + length + " to ambe audio list to match jmbe list size");
            }

            try
            {
                WaveUtils.writeBE(jmbeAudioData, ambeAudioData, stereoOutput);
                WaveUtils.writeBE(jmbeAudioData, monoJmbe);
                WaveUtils.writeBE(ambeAudioData, monoAmbe);
                LOG.info("Wave audio file created at: " + stereoOutput);
//                new Alert(Alert.AlertType.INFORMATION, "Synthesized audio saved to " + outputFile);
            }
            catch(Exception e)
            {
                LOG.error("Error writing wave file", e);
//                new Alert(Alert.AlertType.ERROR, "Error writing synthesized audio to " + outputFile);
            }

            if(AMBE_PROTOCOLS.contains(sequence.getProtocol()))
            {
                List<AMBEModelParameters> parameters = getAMBEModelParameters(sequence);
                List<Integer> harmonics = getHarmonicCounts(parameters);
                List<Float> fundamentals = getFundamentalFrequencies(parameters);
                AnalysisResults results = Analyzer.process(jmbeAudioData, jmbeAudioDataEnhanced, ambeAudioData,
                        fundamentals, harmonics);

                List<double[]> jmbeAmplitudes = results.jmbeAmplitudes();
                List<double[]> jmbeEnhancedAmplitudes = results.jmbeEnhancedAmplitudes();
                List<double[]> ambeAmplitudes = results.ambeAmplitudes();
                List<float[]> jmbePcm = results.jmbePcm();
                List<float[]> ambePcm = results.ambePcm();

                if(jmbeAmplitudes.size() != mFrames.size())
                {
                    LOG.info("Observable frames size [" + mFrames.size() + "] does match amplitudes size [" + jmbeAmplitudes.size() + "]");
                }

                for(int i = 0; i < jmbeAmplitudes.size(); i++)
                {
                    if(mFrames.size() > i)
                    {
                        ObservableMBEFrame frame = mFrames.get(i);
                        frame.setJmbeAmplitudes(jmbeAmplitudes.get(i));
                        frame.setJmbeEnhancedAmplitudes(jmbeEnhancedAmplitudes.get(i));
                        frame.setAmbeAmplitudes(ambeAmplitudes.get(i));
                        frame.setJmbeAudioPcm(jmbePcm.get(i));
                        frame.setAmbeAudioPcm(ambePcm.get(i));
                    }
                }
            }

        }).start();
    }

    private static  List<byte[]> getIMBEAudio(MBECallSequence sequence, boolean enhanceSpectralAmplitudes)
    {
        //Invoke the synthesize for IMBE audio
        IMBESynthesizer synthesizer = new IMBESynthesizer();
        synthesizer.setEnhanceSpectralAmplitudes(enhanceSpectralAmplitudes);
//            synthesizer.setComfortNoiseGeneratorGain(0.4f);

        List<byte[]> imbeAudioData = new ArrayList<>();

        for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
        {
            float[] samples = synthesizer.getAudio(new IMBEFrame(voiceFrame.getFrameBytes()));
            ByteBuffer pcm = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for(float sample : samples)
            {
                pcm.putShort((short)(sample * Short.MAX_VALUE));
            }

            imbeAudioData.add(pcm.array());
        }

        return imbeAudioData;
    }

    private static List<byte[]> getAMBEAudio(MBECallSequence sequence, boolean enhanceSpectralAmplitudes)
    {
        //Invoke the synthesize for IMBE audio
        AMBESynthesizer synthesizer = new AMBESynthesizer();
        synthesizer.setEnhanceSpectralAmplitudes(enhanceSpectralAmplitudes);
        synthesizer.setAGC(false);
//            synthesizer.setComfortNoiseGeneratorGain(0.4f);

        List<byte[]> ambeAudioData = new ArrayList<>();

        for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
        {
            float[] samples = synthesizer.getAudio(new AMBEFrame(voiceFrame.getFrameBytes()));
            ByteBuffer pcm = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.BIG_ENDIAN);
            for(float sample : samples)
            {
                pcm.putShort((short)(sample * Short.MAX_VALUE));
            }

            ambeAudioData.add(pcm.array());
        }

        return ambeAudioData;
    }

    private static List<Integer> getHarmonicCounts(List<AMBEModelParameters> parameters)
    {
        List<Integer> harmonics = new ArrayList<>();
        for(MBEModelParameters parameter: parameters)
        {
            harmonics.add(parameter.getL());
        }

        return harmonics;
    }

    private static List<Float> getFundamentalFrequencies(List<AMBEModelParameters> parameters)
    {
        List<Float> fundamentals = new ArrayList<>();
        for(MBEModelParameters parameter: parameters)
        {
            fundamentals.add(parameter.getFundamentalFrequency());
        }

        return fundamentals;
    }

    private static List<AMBEModelParameters> getAMBEModelParameters(MBECallSequence sequence)
    {
        List<AMBEModelParameters> parameters = new ArrayList<>();

        AMBEModelParameters previous = new AMBEModelParameters();
        AMBEModelParameters next;

        for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
        {
            AMBEFrame frame = new AMBEFrame(voiceFrame.getFrameBytes());

            if(frame.getFrameType() == FrameType.TONE)
            {
                next = new AMBEModelParameters();
            }
            else
            {
                next = frame.getVoiceParameters(previous);
            }

            parameters.add(next);
            previous = next;
        }

        return parameters;
    }

}
