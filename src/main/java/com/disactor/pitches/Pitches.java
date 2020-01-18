package com.disactor.pitches;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import org.jfugue.player.Player;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Pitches extends JFrame implements PitchDetectionHandler {

    private static final String[] LETTERS = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private static final String[] OCTAVES = new String[]{"2", "3", "4", "5", "6"};

    private static final Set<String> EXCLUDES = new LinkedHashSet<>(Arrays.asList("C2", "C#2", "D2", "D#2", "D#6", "E6", "F6", "F#6", "G6", "G#6", "A6", "A#6", "B6"));

    private static final List<String> NOTES = Stream.of(OCTAVES)
            .flatMap(octave -> Stream.of(LETTERS)
                    .map(letter -> letter + octave))
            .filter(note -> !EXCLUDES.contains(note))
            .collect(Collectors.toList());

    private static final Map<String, Pitch> PITCH_BY_NOTE = Stream.of(Pitch.values())
            .collect(Collectors.toMap(Pitch::getNote, pitch -> pitch));

    public static final PitchEstimationAlgorithm DEFAULT_PITCH_ALGO = PitchEstimationAlgorithm.MPM;

    private final AtomicReference<Pitch> prevRiddle = new AtomicReference<>(null);
    private final AtomicReference<Pitch> riddle = new AtomicReference<>(null);
    private final AtomicReference<Pitch> guess = new AtomicReference<>(null);
    private final Player player = new Player();
    private final Random random = new Random();

    private final JTextArea textArea;
    private volatile AudioDispatcher dispatcher;
    private volatile Mixer currentMixer;
    private volatile PitchEstimationAlgorithm algo = DEFAULT_PITCH_ALGO;

    private void play(float pitch, float probability, double rms) {
        Pitch riddle;
        while ((riddle = this.riddle.get()) == null) {
            int index = random.nextInt(NOTES.size());
            String riddleNote = NOTES.get(index);
            riddle = PITCH_BY_NOTE.get(riddleNote);

            Pitch prevRiddle = this.prevRiddle.get();
            if (riddle != null && (prevRiddle == null || !riddle.getBaseChroma().equals(prevRiddle.getBaseChroma()))) {
                out(" [" + riddle.getBaseChroma() + "] is the new riddle");
                this.riddle.set(riddle);
                player.play(riddleNote);
            }
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
                String riddleBaseChroma = this.riddle.get().getBaseChroma();
                String guessBaseChroma = guess.getBaseChroma();
                String message = String.format("  [%s] %s     [%s]%s|%.2fHz|%.2fHz|%.2f|%.5f", riddleBaseChroma, guessBaseChroma, guess.getChroma(), riddle.getChroma(), guess.getPitch(), pitch, probability, rms);
                if (guessBaseChroma.equals(riddleBaseChroma)) {
                    this.prevRiddle.set(riddle);
                    this.riddle.set(null);
                    this.guess.set(null);
                    out(message);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    play(-1, 0, 0);
                } else {
                    player.play(this.riddle.get().getNote());
                    out(message);
                }
            }
        }
    }

    public static void main(String... strings) throws InterruptedException,
            InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                //ignore failure to set default look en feel;
            }
            Pitches pitches = new Pitches();
            pitches.pack();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            pitches.setLocation(200, screen.height / 2 - pitches.getSize().height / 2);

            pitches.setVisible(true);
            pitches.player.play("C3 C4 C5");
            pitches.play(-1, 0, 0);

            for (Mixer.Info info : Shared.getMixerInfo(false, true)) {
                if (info.toString().contains("Default")) {
                    Mixer newValue = AudioSystem.getMixer(info);
                    try {
                        pitches.setNewMixer(newValue);
                    } catch (LineUnavailableException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        });
    }

    @Override
    public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
        if (pitchDetectionResult.getPitch() != -1) {
            float pitch = pitchDetectionResult.getPitch();
            float probability = pitchDetectionResult.getProbability();
            double rms = audioEvent.getRMS() * 100;
            play(pitch, probability, rms);
        }
    }

    public Pitches() {
        this.setLayout(new GridLayout(0, 1));
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setTitle("Pitches");

        JPanel inputPanel = new InputPanel();
        add(inputPanel);
        inputPanel.addPropertyChangeListener("mixer",
                event -> {
                    try {
                        setNewMixer((Mixer) event.getNewValue());
                    } catch (LineUnavailableException e) {
                        e.printStackTrace();
                    }
                });

        ActionListener algoChangeListener = e -> {
            String name = e.getActionCommand();
            algo = PitchEstimationAlgorithm.valueOf(name);
            try {
                setNewMixer(currentMixer);
            } catch (LineUnavailableException e1) {
                e1.printStackTrace();
            }
        };
        JPanel pitchDetectionPanel = new PitchDetectionPanel(algoChangeListener);
        add(pitchDetectionPanel);

        textArea = new JTextArea();
        textArea.setEditable(false);
        add(new JScrollPane(textArea));
    }

    private void setNewMixer(Mixer mixer) throws LineUnavailableException {
        if (dispatcher != null) {
            dispatcher.stop();
        }
        currentMixer = mixer;

        float sampleRate = 44100;
        int bufferSize = 1024;
        int overlap = 0;

        textArea.append("Started listening with " + Shared.toLocalString(mixer.getMixerInfo().getName()) + "\n");

        final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
        final DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine line;
        line = (TargetDataLine) mixer.getLine(dataLineInfo);
        line.open(format, bufferSize);
        line.start();
        final AudioInputStream stream = new AudioInputStream(line);

        JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);
        dispatcher = new AudioDispatcher(audioStream, bufferSize, overlap);
        dispatcher.addAudioProcessor(new PitchProcessor(algo, sampleRate, bufferSize, this));

        new Thread(dispatcher, "Audio dispatching").start();
    }

    private void out(String message) {
        message = message + "\n";
        textArea.append(message);
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }

    public static class InputPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        Mixer mixer = null;

        public InputPanel() {
            super(new BorderLayout());
            this.setBorder(new TitledBorder("Microphone input"));
            JPanel buttonPanel = new JPanel(new GridLayout(0, 1));
            ButtonGroup group = new ButtonGroup();
            for (Mixer.Info info : Shared.getMixerInfo(false, true)) {
                JRadioButton button = new JRadioButton();
                button.setText(Shared.toLocalString(info));
                buttonPanel.add(button);
                group.add(button);
                button.setActionCommand(info.toString());
                button.addActionListener(event -> {
                    String command = event.getActionCommand();
                    setMixer(command);
                });
                if (info.toString().contains("Default")) {
                    button.setSelected(true);
                }
            }

            this.add(new JScrollPane(buttonPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
            this.setMaximumSize(new Dimension(300, 150));
            this.setPreferredSize(new Dimension(300, 150));
        }

        private void setMixer(String command) {
            for (Mixer.Info info : Shared.getMixerInfo(false, true)) {
                if (command.equals(info.toString())) {
                    Mixer newValue = AudioSystem.getMixer(info);
                    InputPanel.this.firePropertyChange("mixer", mixer, newValue);
                    InputPanel.this.mixer = newValue;
                    break;
                }
            }
        }

    }

    public static class PitchDetectionPanel extends JPanel {
        private static final long serialVersionUID = -5107785666165487335L;

        public PitchDetectionPanel(ActionListener algoChangedListener) {
            super(new GridLayout(0, 1));
            setBorder(new TitledBorder("Pitch detection algorithm"));
            ButtonGroup group = new ButtonGroup();
            for (PitchEstimationAlgorithm value : PitchEstimationAlgorithm.values()) {
                JRadioButton button = new JRadioButton();
                button.setText(value.toString());
                add(button);
                group.add(button);
                button.setSelected(value == DEFAULT_PITCH_ALGO);
                button.setActionCommand(value.name());
                button.addActionListener(algoChangedListener);
            }
        }
    }

}
