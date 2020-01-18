package com.disactor.pitches;

import be.tarsos.dsp.pitch.PitchDetectionHandler;
import org.jfugue.player.Player;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;


public class Pitches extends JFrame implements PitchDetectionHandler {

    private static final String[] letters = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] octaves = new String[]{"2", "3", "4", "5", "6"};
    private static final Set<String> exclude = new LinkedHashSet<>(Arrays.asList("C2", "C#2", "D2", "D#2", "D#6", "E6", "F6", "F#6", "G6", "G#6", "A6", "A#6", "B6"));
    private static final List<String> notes = Stream.of(octaves)
            .flatMap(octave -> Stream.of(letters)
                    .map(letter -> letter + octave))
            .filter(note -> !exclude.contains(note))
            .collect(Collectors.toList());

    private final Map<String, Pitch> pitchByNote = new LinkedHashMap<>();

    {
        for (Pitch pitch : Pitch.values()) {
            pitchByNote.put(pitch.getNote(), pitch);
        }
    }

    private final AtomicReference<Pitch> riddle = new AtomicReference<>(null);
    private final AtomicReference<Pitch> guess = new AtomicReference<>(null);
    private final Player player = new Player();
    private final Random random = new Random();

    @Override
    public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
        if (pitchDetectionResult.getPitch() != -1) {
            float pitch = pitchDetectionResult.getPitch();
            float probability = pitchDetectionResult.getProbability();
            double rms = audioEvent.getRMS() * 100;

            play(pitch, probability, rms);
        }
    }

    private void play(float pitch, float probability, double rms) {
        if (this.riddle.get() == null) {
            int index = random.nextInt(notes.size());
            String riddleNote = notes.get(index);
            Pitch riddle = pitchByNote.get(riddleNote);
            String chrome = "";
            if (riddle != null) {
                chrome = riddle.getChrome();
            }
            out("new riddle is [" + chrome.substring(0, 2) + "]");
            this.riddle.set(riddle);
            player.play(riddleNote);
        }


        if (probability > 0.5) {
            Pitch guess = null;
            for (Pitch aPitch : Pitch.values()) {
                double diff = Math.abs(aPitch.getPitch() - pitch);
                if (diff < 1) {
                    if (guess != null) {
                        if (Math.abs(guess.getPitch() - pitch) < diff) {
                            aPitch = guess;
                        }
                    }
                    guess = aPitch;
                }
            }
            if (guess != null) {
                Pitch previousGuess = this.guess.get();
                if (previousGuess == null || previousGuess != guess) {
                    this.guess.set(guess);
                    Pitch riddle = this.riddle.get();
                    if (riddle != null) {
                        String baseRiddleChrome = riddle.getChrome().substring(0, 2);
                        String baseGuessChrome = guess.getChrome().substring(0, 2);
                        player.play(guess.getNote());
                        if (baseGuessChrome.equals(baseRiddleChrome)) {
                            out("correct");
                            this.riddle.set(null);
                            this.guess.set(null);
                            play(-1, 0, 0);
                        } else {
                            player.play(riddle.getNote());
                        }
//                        String message = String.format(" %s [%s]  %s|%s %.2fHz|%.2fHz|%.2f|%.5f", baseGuessChrome, baseRiddleChrome, guess.getChrome(), riddle.getChrome(), guess.getPitch(), pitch, probability, rms);
                        String message = String.format("  %s [%s]", baseGuessChrome, baseRiddleChrome);
                        out(message);
                    }
                }
            }
        }
    }

    private void out(String message) {
        message = message + "\n";
        textArea.append(message);
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }


    public static void main(String... strings) throws InterruptedException,
            InvocationTargetException {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    //ignore failure to set default look en feel;
                }
                JFrame frame = new Pitches();
                frame.pack();
                frame.setVisible(true);
            }
        });


    }

    private final JTextArea textArea;

    private AudioDispatcher dispatcher;
    private Mixer currentMixer;

    private PitchEstimationAlgorithm algo;
    private ActionListener algoChangeListener = new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
            String name = e.getActionCommand();
            PitchEstimationAlgorithm newAlgo = PitchEstimationAlgorithm.valueOf(name);
            algo = newAlgo;
            try {
                setNewMixer(currentMixer);
            } catch (LineUnavailableException e1) {
                e1.printStackTrace();
            } catch (UnsupportedAudioFileException e1) {
                e1.printStackTrace();
            }
        }
    };

    public Pitches() {
        this.setLayout(new GridLayout(0, 1));
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setTitle("Pitch Detector");

        JPanel inputPanel = new InputPanel();
        add(inputPanel);
        inputPanel.addPropertyChangeListener("mixer",
                new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent arg0) {
                        try {
                            setNewMixer((Mixer) arg0.getNewValue());
                        } catch (LineUnavailableException | UnsupportedAudioFileException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });

        algo = PitchEstimationAlgorithm.MPM;

        JPanel pitchDetectionPanel = new PitchDetectionPanel(algoChangeListener);

        add(pitchDetectionPanel);


        textArea = new JTextArea();
        textArea.setEditable(false);
        add(new JScrollPane(textArea));
    }

    private void setNewMixer(Mixer mixer) throws LineUnavailableException,
            UnsupportedAudioFileException {

        if (dispatcher != null) {
            dispatcher.stop();
        }
        currentMixer = mixer;

        float sampleRate = 44100;
        int bufferSize = 1024;
        int overlap = 0;

        textArea.append("Started listening with " + Shared.toLocalString(mixer.getMixerInfo().getName()) + "\n");

        final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true,
                true);
        final DataLine.Info dataLineInfo = new DataLine.Info(
                TargetDataLine.class, format);
        TargetDataLine line;
        line = (TargetDataLine) mixer.getLine(dataLineInfo);
        final int numberOfSamples = bufferSize;
        line.open(format, numberOfSamples);
        line.start();
        final AudioInputStream stream = new AudioInputStream(line);

        JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);
        // create a new dispatcher
        dispatcher = new AudioDispatcher(audioStream, bufferSize,
                overlap);

        // add a processor
        dispatcher.addAudioProcessor(new PitchProcessor(algo, sampleRate, bufferSize, this));

        new Thread(dispatcher, "Audio dispatching").start();
    }


    public class InputPanel extends JPanel {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        Mixer mixer = null;

        public InputPanel() {
            super(new BorderLayout());
            this.setBorder(new TitledBorder("1. Choose a microphone input"));
            JPanel buttonPanel = new JPanel(new GridLayout(0, 1));
            ButtonGroup group = new ButtonGroup();
            String selected = null;
            for (Mixer.Info info : Shared.getMixerInfo(false, true)) {
                JRadioButton button = new JRadioButton();
                button.setText(Shared.toLocalString(info));
                buttonPanel.add(button);
                group.add(button);
                button.setActionCommand(info.toString());
                button.addActionListener(setInput);
                //fixme:
                if (info.toString().contains("Default") || info.toString().contains("default")) {
                    selected = info.toString();
                }
            }

            this.add(new JScrollPane(buttonPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
            this.setMaximumSize(new Dimension(300, 150));
            this.setPreferredSize(new Dimension(300, 150));
            setMixer(selected);

        }

        private ActionListener setInput = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                String command = arg0.getActionCommand();
                setMixer(command);
            }

        };

        private void setMixer(String commain) {
            for (Mixer.Info info : Shared.getMixerInfo(false, true)) {
                if (commain.equals(info.toString())) {
                    Mixer newValue = AudioSystem.getMixer(info);
                    InputPanel.this.firePropertyChange("mixer", mixer, newValue);
                    InputPanel.this.mixer = newValue;
                    break;
                }
            }
        }

    }


    public class PitchDetectionPanel extends JPanel {

        /**
         *
         */
        private static final long serialVersionUID = -5107785666165487335L;

        private PitchEstimationAlgorithm algo;

        public PitchDetectionPanel(ActionListener algoChangedListener) {
            super(new GridLayout(0, 1));
            setBorder(new TitledBorder("2. Choose a pitch detection algorithm"));
            ButtonGroup group = new ButtonGroup();
//            algo = PitchEstimationAlgorithm.YIN;
            algo = PitchEstimationAlgorithm.MPM;
            for (PitchEstimationAlgorithm value : PitchEstimationAlgorithm.values()) {
                JRadioButton button = new JRadioButton();
                button.setText(value.toString());
                add(button);
                group.add(button);
                button.setSelected(value == algo);
                button.setActionCommand(value.name());
                button.addActionListener(algoChangedListener);
            }
        }
    }


}
