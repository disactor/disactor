package com.disactor.pitchenation;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import com.google.common.collect.ImmutableMap;
import org.jfugue.player.Player;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Pitchenation extends JFrame implements PitchDetectionHandler {

    private static final String[] CHROMATIC_SCALE = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] DIATONIC_SCALE = new String[]{"C", "D", "E", "F", "G", "A", "B"};

    //        private static final String[] OCTAVES = new String[]{"2", "3", "4", "5", "6"};
    private static final String[] OCTAVES = new String[]{"3", "4", "5", "6", "7"};

    //        private static final Set<String> EXCLUDES = new LinkedHashSet<>(Arrays.asList("C2", "C#2", "D2", "D#2", "D#6", "E6", "F6", "F#6", "G6", "G#6", "A6", "A#6", "B6"));
    private static final Set<String> EXCLUDES = new LinkedHashSet<>(Arrays.asList("C3", "C#3", "D3", "D#3", "D#7", "E7", "F7", "F#7", "G7", "G#7", "A7", "A#7", "B7"));
//    private static final Set<String> EXCLUDES = Collections.emptySet();

    private static final List<String> chromaticNotes = Stream.of(OCTAVES)
            .flatMap(octave -> Stream.of(CHROMATIC_SCALE)
                    .map(letter -> letter + octave))
            .filter(note -> !EXCLUDES.contains(note))
            .collect(Collectors.toList());

    private static final List<String> diatonicNotes = Stream.of(OCTAVES)
            .flatMap(octave -> Stream.of(DIATONIC_SCALE)
                    .map(letter -> letter + octave))
            .filter(note -> !EXCLUDES.contains(note))
            .collect(Collectors.toList());


    private static final Map<String, Color> chromaToColor = new ImmutableMap.Builder<String, Color>()
            .put("Mi", new MyColor(98.0f, 43.5f, 98.8f))
            .put("Fa", new MyColor(0.8f, 98.8f, 14.5f))
            .put("Fi", new MyColor(1.2f, 34.5f, 27.1f))
            .put("So", new MyColor(98.8f, 0.8f, 0.8f))
            .put("Se", new MyColor(6.3f, 9.4f, 41.6f))
            .put("La", new MyColor(98.4f, 96.1f, 67.8f))
            .put("Li", new MyColor(0.8f, 95.3f, 98.8f))
            .put("Si", new MyColor(85.9f, 75.3f, 95.7f))
            .put("Do", new MyColor(99.2f, 79.6f, 1.2f))
//            .put("Di", new MyColor(25.4f, 1.2f, 29.4f)) // original Di color, do not remove
            .put("Di", new MyColor(45.4f, 1.2f, 29.4f))
            .put("Re", new MyColor(1.2f, 70.2f, 99.2f))
//            .put("Ri", new MyColor(95.7f, 22.0f, 2.4f)) // original Ri color, do not remove
            .put("Ri", new Color(255, 113, 0))
            .build();

    private static final Map<String, Pitch> pitchByNote = Stream.of(Pitch.values())
            .collect(Collectors.toMap(Pitch::getNote, pitch -> pitch));

    public static final PitchEstimationAlgorithm DEFAULT_PITCH_ALGO = PitchEstimationAlgorithm.MPM;

    public static final int OCTAVE_TOLERANCE = 9;
    public static final int OCTAVE_CORRECTION = -1;

    private final AtomicReference<Pitch> prevRiddle = new AtomicReference<>(null);
    private final AtomicReference<Pitch> riddle = new AtomicReference<>(null);
    private final AtomicReference<Pitch> guess = new AtomicReference<>(null);
    private final Player player = new Player();
    private final Random random = new Random();
    private final List<String> detected = new LinkedList<>();
    private static final int detectionThreshold = 4;
    private final JTextArea textArea = new JTextArea();
    private final JPanel riddleColorPanel = new JPanel();
    private final JPanel guessColorPanel = new JPanel();
    private final JLabel guessColorLabel = new JLabel();
    private final JLabel riddleColorLabel = new JLabel();
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isChromatic = new AtomicBoolean(false);
    private volatile AudioDispatcher dispatcher;
    private volatile Mixer currentMixer;
    private volatile PitchEstimationAlgorithm algo = DEFAULT_PITCH_ALGO;

    private void play(float pitch, float probability, double rms, Pitch guess) {
        while (this.riddle.get() == null) {
            List<String> notes = (isChromatic.get() ? chromaticNotes : diatonicNotes);
            int index = random.nextInt(notes.size());
            String riddleNote = notes.get(index);
            Pitch riddle = pitchByNote.get(riddleNote);

            Pitch prevRiddle = this.prevRiddle.get();
            if (riddle != null && (prevRiddle == null || !riddle.getBaseChroma().equals(prevRiddle.getBaseChroma()))) {
                out(" [" + riddle.getBaseChroma() + "] is the new riddle");
                this.riddle.set(riddle);
                SwingUtilities.invokeLater(() -> {
                    Color riddleColor = chromaToColor.get(riddle.getBaseChroma());
                    riddleColorLabel.setText("");
                    riddleColorPanel.setBackground(riddleColor);
                });
                player.play("I[Piano] " + riddleNote);
            }
        }

        if (probability > 0.5) {
            if (guess != null) {
                Pitch riddle = this.riddle.get();
                SwingUtilities.invokeLater(() -> {
                    guessColorLabel.setText(" " + guess.getChroma() + " ");
                    guessColorPanel.setBackground(chromaToColor.get(guess.getBaseChroma()));
                });
                String message = String.format("  [%s] %s  -  [%s] %s [%.2fHz] %.2fHz - %.2f | %.5f", riddle.getBaseChroma(), guess.getBaseChroma(), riddle.getChroma(), guess.getChroma(), riddle.getPitch(), pitch, probability, rms);
                if (guess.getBaseChroma().equals(riddle.getBaseChroma())
                        && Math.abs(guess.getOctave() - riddle.getOctave() - OCTAVE_CORRECTION) <= OCTAVE_TOLERANCE
                        && isRunning.get()) {
                    SwingUtilities.invokeLater(() -> riddleColorLabel.setText(" " + riddle.getChroma() + " "));
                    this.prevRiddle.set(riddle);
                    this.riddle.set(null);
                    this.guess.set(null);

                    if (isRunning.get()) {
                        player.play("I[Piano] " + riddle.getNote());
                    }
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    play(-1, 0, 0, guess);
                } else {
                    if (isRunning.get()) {
                        player.play("I[Piano] " + riddle.getNote());
                    }
                }
                out(message);
            }
        }
    }

    private Pitch matchPitch(float pitch) {
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
        return guess;
    }

    public static void main(String... strings) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                //ignore failure to set default look en feel;
            }
            new Pitchenation();
        });
    }

    @Override
    public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
        if (pitchDetectionResult.getPitch() != -1) {
            float pitch = pitchDetectionResult.getPitch();
            float probability = pitchDetectionResult.getProbability();
            double rms = audioEvent.getRMS() * 100;
            Pitch guess = matchPitch(pitch);
            if (guess != null) {
                if (detected.size() < detectionThreshold) {
                    detected.removeIf(aDetected -> !guess.getBaseChroma().equals(aDetected));
                    detected.add(guess.getBaseChroma());
                    String message = String.format("    %s %.2fHz - %.2f | %.5f", guess.getChroma(), pitch, probability, rms);
                    out(message);
                } else {
                    detected.clear();
                    play(pitch, probability, rms, guess);
                }
            }
        }
    }

    public Pitchenation() {
        setLayout(new GridLayout(6, 1));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Pitchenation");

        JPanel controlPanel = new JPanel();
        add(controlPanel);
        controlPanel.setLayout(new GridBagLayout());

        JButton startStopButton = new JButton();
        controlPanel.add(startStopButton);
        SwingUtilities.invokeLater(() -> startStopButton.setText("Stop"));
        startStopButton.setAction(
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        System.out.println(isRunning.get());
                        boolean updated = isRunning.compareAndSet(true, false);
                        System.out.println("\n" + updated);
                        if (updated) {
                            startStopButton.setText("Start");
                        } else {
                            updated = isRunning.compareAndSet(false, true);
                            System.out.println(updated);
                            if (updated) {
                                startStopButton.setText("Stop");
                            } else {
                                System.out.println("Weird - should not happen");
                            }
                        }
                    }
                }
        );

        JButton chromaticButton = new JButton();
        controlPanel.add(chromaticButton);
        SwingUtilities.invokeLater(() -> chromaticButton.setText("Switch to chromatic"));
        chromaticButton.setAction(
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        System.out.println(isChromatic.get());
                        boolean updated = isChromatic.compareAndSet(false, true);
                        System.out.println("\n" + updated);
                        if (updated) {
                            chromaticButton.setText("Switch to diatonic");
                        } else {
                            updated = isChromatic.compareAndSet(true, false);
                            System.out.println(updated);
                            if (updated) {
                                chromaticButton.setText("Switch to chromatic");
                            } else {
                                System.out.println("Weird - should not happen");
                            }
                        }
                        startStopButton.requestFocus();
                    }
                }
        );


        JPanel inputPanel = new InputPanel();

        add(inputPanel);
        inputPanel.addPropertyChangeListener("mixer", event -> {
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
        JPanel pitchAlgoPanel = new PitchAlgoPanel(algoChangeListener);

        add(pitchAlgoPanel);

        JPanel colorsPanel = new JPanel();

        add(colorsPanel);
        colorsPanel.setLayout(new GridLayout(1, 12));
        for (Map.Entry<String, Color> chromaColor : chromaToColor.entrySet()) {
            JPanel colorPanel = new JPanel();
            colorsPanel.add(colorPanel);
            colorPanel.setBackground(chromaColor.getValue());
            JLabel colorLabel = new JLabel(" " + chromaColor.getKey() + " ");
            colorPanel.add(colorLabel);
            colorLabel.setForeground(Color.WHITE);
            colorLabel.setBackground(Color.BLACK);
            colorLabel.setOpaque(true);
        }

//        textArea.setEditable(false);
//        add(new JScrollPane(textArea));
        riddleColorPanel.setBackground(chromaToColor.get("Do"));

        add(riddleColorPanel);

        riddleColorPanel.add(riddleColorLabel);
        riddleColorLabel.setForeground(Color.WHITE);
        riddleColorLabel.setBackground(Color.BLACK);
        riddleColorLabel.setOpaque(true);

        add(guessColorPanel);
        guessColorLabel.setForeground(Color.WHITE);
        guessColorLabel.setBackground(Color.BLACK);
        guessColorLabel.setOpaque(true);
        guessColorLabel.setVerticalAlignment(SwingConstants.CENTER);
        guessColorPanel.add(guessColorLabel);

        pack();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(screen.width - getSize().width - 100, screen.height / 2 - getSize().height / 2);

        setVisible(true);

        Executors.newSingleThreadExecutor().

                execute(() ->

                {
                    player.play("C3");
                    play(-1, 0, 0, matchPitch(-1));

                    for (Mixer.Info info : Shared.getMixerInfo(false, true)) {
                        if (info.toString().contains("Default")) {
                            Mixer newValue = AudioSystem.getMixer(info);
                            try {
                                setNewMixer(newValue);
                            } catch (LineUnavailableException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                    }
                });
    }

    private void setNewMixer(Mixer mixer) throws LineUnavailableException {
        if (dispatcher != null) {
            dispatcher.stop();
        }
        currentMixer = mixer;

        float sampleRate = 44100;
        int bufferSize = 1024;
        int overlap = 0;

        out("Started listening with " + Shared.toLocalString(mixer.getMixerInfo().getName()) + "\n");

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
        System.out.println(message);
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
            this.setMaximumSize(new Dimension(400, 200));
            this.setPreferredSize(new Dimension(400, 200));
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

    public static class PitchAlgoPanel extends JPanel {
        private static final long serialVersionUID = -5107785666165487335L;

        public PitchAlgoPanel(ActionListener algoChangedListener) {
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

    public static class MyColor extends Color {

        public MyColor(float r, float g, float b) {
            super(r / 100, g / 100, b / 100);
        }

        public MyColor(float r, float g, float b, float a) {
            super(r, g, b, a);
        }

        public MyColor(ColorSpace cspace, float[] components, float alpha) {
            super(cspace, components, alpha);
        }
    }

}
