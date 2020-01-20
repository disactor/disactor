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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Pitchenation extends JFrame implements PitchDetectionHandler {

    private static final String[] scale = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] octaves = new String[]{"3", "4", "5", "6", "7"};
    private static final Set<String> excludes = new LinkedHashSet<>(Arrays.asList("C3", "C#3", "D3", "D#3", "D#7", "E7", "F7", "F#7", "G7", "G#7", "A7", "A#7", "B7"));
    private static final List<String> notes = Stream.of(octaves)
            .flatMap(octave -> Stream.of(scale)
                    .map(letter -> letter + octave))
            .filter(note -> !excludes.contains(note))
            .collect(Collectors.toList());

    //    tuning: Mi2 | pitch=82.56Hz | diff=0.15 | pitchy=4.90 | percent=3.02 | width=9
// https://www.nature.com/articles/s41598-017-18150-y/figures/2
    private static final Map<String, Color> chromaToColor = new ImmutableMap.Builder<String, Color>()
            .put("Do", new Color(255, 0, 0)) //    Red: 620-750 nm (400-484 THz frequency)
            .put("Di", new Color(127, 0,0))
            .put("Re", new Color(255, 127, 0))    //    Orange: 590-620 nm
            .put("Ri", new Color(127, 127, 0))
            .put("Mi", new Color(255, 255, 0))   //    Yellow: 570-590 nm
            .put("Fa", new Color(0, 255, 0))  //    Green: 495-570 nm
            .put("Fi", new Color(0, 127,127))
            .put("So", new Color(0, 255, 255)) // Light-blue
            .put("Se", new Color(0, 0, 127))
            .put("La", new Color(0, 0, 255)) //    Blue: 450-495 nm
            .put("Li", new Color(127, 0, 127))
            .put("Si", new Color(127, 0, 255)) //    Violet: 380-450 nm (688-789 THz frequency)
            .build();

    //    private static final Map<String, Color> chromaToColor = new ImmutableMap.Builder<String, Color>()
//            .put("Do", new MyColor(99.2f, 79.6f, 1.2f)) // .put("Di", new MyColor(25.4f, 1.2f, 29.4f)) // original Di color
//            .put("Di", new MyColor(45.4f, 1.2f, 29.4f))
//            .put("Re", new MyColor(1.2f, 70.2f, 99.2f)) // .put("Ri", new MyColor(95.7f, 22.0f, 2.4f)) // original Ri color
//            .put("Ri", new Color(255, 113, 0))
//            .put("Mi", new MyColor(98.0f, 43.5f, 98.8f))
//            .put("Fa", new MyColor(0.8f, 98.8f, 14.5f))
//            .put("Fi", new MyColor(1.2f, 34.5f, 27.1f))
//            .put("So", new MyColor(98.8f, 0.8f, 0.8f))
//            .put("Se", new MyColor(6.3f, 9.4f, 41.6f))
//            .put("La", new MyColor(98.4f, 96.1f, 67.8f))
//            .put("Li", new MyColor(0.8f, 95.3f, 68.8f)) // .put("Li", new MyColor(0.8f, 95.3f, 98.8f)) // original Li color
//            .put("Si", new MyColor(85.9f, 75.3f, 95.7f))
//            .build();
    private static final Map<String, Pitch> pitchByNote = Stream.of(Pitch.values())
            .collect(Collectors.toMap(Pitch::getNote, pitch -> pitch));
    private static final Map<Integer, Pitch> pitchByOrdinal = Stream.of(Pitch.values())
            .collect(Collectors.toMap(Pitch::ordinal, pitch -> pitch));
    private static final PitchEstimationAlgorithm defaultPitchAlgo = PitchEstimationAlgorithm.MPM;
    private static final Pitch playOnSuccess = Pitch.Do4;

    private final JPanel riddlePanel;
    private final JPanel guessPanel;
    private final JLabel guessLabel;
    private final JLabel riddleLabel;

    private final AtomicReference<Pitch> prevRiddle = new AtomicReference<>(null);
    private final AtomicReference<Pitch> riddle = new AtomicReference<>(null);
    private final AtomicReference<Pitch> guess = new AtomicReference<>(null);
    private final Player player = new Player();
    private final Random random = new Random();
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final JPanel flatPanel;
    private final JPanel sharpPanel;
    private volatile AudioDispatcher dispatcher;
    private volatile Mixer currentMixer;
    private volatile PitchEstimationAlgorithm algo = defaultPitchAlgo;

    private void play(float pitch, float probability, double rms, Pitch guess) {
        if (isRunning.get()) {
            while (this.riddle.get() == null) {
                int index = random.nextInt(notes.size());
                String riddleNote = notes.get(index);
                Pitch riddle = pitchByNote.get(riddleNote);
                Pitch prevRiddle = this.prevRiddle.get();
                if (riddle != null
                        && (prevRiddle == null || !riddle.getChroma().equals(prevRiddle.getChroma()))) {
                    out(" [" + riddle.getEchroma() + "] is the new riddle");
                    this.riddle.set(riddle);
                    SwingUtilities.invokeLater(() -> {
                        Color riddleColor = chromaToColor.get(riddle.getChroma());
                        riddleLabel.setText(" " + riddle.getChroma() + " ");
                        riddlePanel.setBackground(riddleColor);
                        guessLabel.setText("    ");
                        guessLabel.setVisible(false);
                        guessPanel.setBackground(null);
                        flatPanel.setVisible(false);
                        sharpPanel.setVisible(false);
                    });
                    player.play(riddleNote);
                }
            }
        }

        if (guess != null) {
            Pitch riddle = this.riddle.get();
            SwingUtilities.invokeLater(() -> updateGuess(pitch, guess));
            if (riddle != null) {
                out(String.format("  [%s] %s  -  [%s] %s [%.2fHz] %.2fHz - %.2f | %.5f", riddle.getChroma(), guess.getChroma(), riddle.getEchroma(), guess.getEchroma(), riddle.getPitch(), pitch, probability, rms));
                if (guess.getChroma().equals(riddle.getChroma()) && isRunning.get()) {
                    SwingUtilities.invokeLater(() -> riddleLabel.setText(" " + riddle.getChroma() + " "));
                    this.prevRiddle.set(riddle);
                    this.riddle.set(null);
                    this.guess.set(null);

                    player.play(riddle.getNote());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    SwingUtilities.invokeLater(() -> {
                        riddleLabel.setText(" " + playOnSuccess.getChroma() + " ");
                        riddlePanel.setBackground(chromaToColor.get(playOnSuccess.getChroma()));
                    });
                    player.play(playOnSuccess.getNote());
                    play(-1, 0, 0, guess);
                } else {
                    if (isRunning.get()) {
                        player.play(riddle.getNote());
                    }
                }
            }
        }
    }

    private void updateGuess(float pitch, Pitch guess) {
        double diff = pitch - guess.getPitch();
        guessLabel.setVisible(true);
        guessLabel.setText(" " + guess.getChroma() + " ");
        guessPanel.setBackground(chromaToColor.get(guess.getChroma()));

        if (!isRunning.get()) {
            Pitch flat = pitchByOrdinal.get(guess.ordinal() - 1);
            Pitch sharp = pitchByOrdinal.get(guess.ordinal() + 1);
            if (flat != null && sharp != null) { // Can be null when out of range, this could have been done better, but who cares?
                flatPanel.setBackground(chromaToColor.get(flat.getChroma()));
                sharpPanel.setBackground(chromaToColor.get(sharp.getChroma()));
                JPanel panel;
                JPanel otherPanel;
                Pitch pitchy;
                if (diff < 0) {
                    panel = flatPanel;
                    otherPanel = sharpPanel;
                    pitchy = flat;
                } else {
                    panel = sharpPanel;
                    otherPanel = flatPanel;
                    pitchy = sharp;
                }
                double pitchyDiff = Math.abs(guess.getPitch() - pitchy.getPitch());
                double percentage = Math.abs(diff) * 100 / pitchyDiff;
                int width = (int) percentage * 3;
                Dimension dimension = new Dimension(width, (int) panel.getPreferredSize().getHeight());
                out(String.format("tuning: %s | pitch=%.2fHz | diff=%.2f | pitchy=%.2f | percent=%.2f | width=%s", guess.getEchroma(), pitch, diff, pitchyDiff, percentage, width));
                panel.setSize(dimension);
                panel.setVisible(true);
                otherPanel.setVisible(false);
            }
        }
    }

    private Pitch matchPitch(float pitch) {
        Pitch guess = null;
        for (Pitch aPitch : Pitch.values()) {
            double diff = Math.abs(aPitch.getPitch() - pitch);
            if (diff < 5) {
//                out("         diff: " + aPitch.getPitch() + "-" + pitch + "=" + diff);
                if (guess != null) {
                    if (Math.abs(guess.getPitch() - pitch) < diff) {
                        aPitch = guess;
//                        out("         better diff: " + aPitch.getPitch() + "-" + pitch + "=" + diff);
                    }
                }
                guess = aPitch;
            }
        }
        return guess;
    }

    public static void main(String... strings) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            UIManager.put("control", new Color(128, 128, 128));
            UIManager.put("info", new Color(128, 128, 128));
            UIManager.put("nimbusBase", new Color(18, 30, 49));
            UIManager.put("nimbusAlertYellow", new Color(248, 187, 0));
            UIManager.put("nimbusDisabledText", new Color(128, 128, 128));
            UIManager.put("nimbusFocus", new Color(115, 164, 209));
            UIManager.put("nimbusGreen", new Color(176, 179, 50));
            UIManager.put("nimbusInfoBlue", new Color(66, 139, 221));
            UIManager.put("nimbusLightBackground", new Color(18, 30, 49));
            UIManager.put("nimbusOrange", new Color(191, 98, 4));
            UIManager.put("nimbusRed", new Color(169, 46, 34));
            UIManager.put("nimbusSelectedText", new Color(255, 255, 255));
            UIManager.put("nimbusSelectionBackground", new Color(104, 93, 156));
            UIManager.put("text", new Color(230, 230, 230));
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    try {
                        UIManager.setLookAndFeel(info.getClassName());
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
            new Pitchenation();
        });
    }

    @Override
    public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
        try {
            if (pitchDetectionResult.getPitch() != -1) {
                float pitch = pitchDetectionResult.getPitch();
                float probability = pitchDetectionResult.getProbability();
                double rms = audioEvent.getRMS() * 100;
                Pitch guess = matchPitch(pitch);
//            String guessEhroma = guess == null ? "" : guess.getEhroma();
//            String message = String.format("    %s %.2fHz - %.2f | %.5f", guessChroma, pitch, probability, rms);
//            out(message);
                if (guess != null) {
                    play(pitch, probability, rms, guess);
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
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
        SwingUtilities.invokeLater(() -> startStopButton.setText("Start"));
        startStopButton.setAction(
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        updateStartStopButton(startStopButton);
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

        JPanel pitchAlgoPanel = new PitchAlgoPanel(event -> {
            String name = event.getActionCommand();
            algo = PitchEstimationAlgorithm.valueOf(name);
            try {
                setNewMixer(currentMixer);
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        });
        add(pitchAlgoPanel);

        riddlePanel = new JPanel();
        add(riddlePanel);
        riddleLabel = new JLabel();
        riddlePanel.add(riddleLabel);
        riddleLabel.setForeground(Color.WHITE);
        riddleLabel.setBackground(Color.BLACK);
        riddleLabel.setOpaque(true);

        guessPanel = new JPanel();
        add(guessPanel);
        JPanel labelsPanel = new JPanel();
        guessPanel.add(labelsPanel);
        labelsPanel.setOpaque(false);
        labelsPanel.setLayout(new GridLayout(2, 1));

        JPanel guessLabelPanel = new JPanel();
        labelsPanel.add(guessLabelPanel);
        guessLabelPanel.setOpaque(false);
        guessLabel = new JLabel();
        guessLabelPanel.add(guessLabel);
        guessLabel.setOpaque(true);
        guessLabel.setVisible(false);
        guessLabel.setText("    ");
        guessLabel.setForeground(Color.WHITE);
        guessLabel.setBackground(Color.BLACK);

        JPanel tunerPanel = new JPanel();
        labelsPanel.add(tunerPanel);
        tunerPanel.setLayout(new GridBagLayout());
        tunerPanel.setOpaque(false);

        JPanel flatHolder = new JPanel();
//        flatHolder.setBorder(BorderFactory.createLineBorder(Color.RED));
        tunerPanel.add(flatHolder, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 10, 0));
        flatHolder.setPreferredSize(new Dimension(200, 20));
        flatHolder.setSize(new Dimension(200, 20));
        flatHolder.setOpaque(false);
        flatHolder.setLayout(new GridBagLayout());
        flatPanel = new JPanel();
        flatHolder.add(flatPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 10, 0));
        flatPanel.setOpaque(true);
        flatPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        JPanel sharpHolder = new JPanel();
//        sharpHolder.setBorder(BorderFactory.createLineBorder(Color.RED));
        tunerPanel.add(sharpHolder, new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 10, 0));
        sharpHolder.setPreferredSize(new Dimension(200, 20));
        sharpHolder.setSize(new Dimension(200, 20));
        sharpHolder.setOpaque(false);
        sharpHolder.setLayout(new GridBagLayout());
        sharpPanel = new JPanel();
        sharpHolder.add(sharpPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 10, 0));
        sharpPanel.setAlignmentX(0);
        sharpPanel.setOpaque(true);
        sharpPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));

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

        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(screen.width - getSize().width - 15, screen.height / 2 - getSize().height / 2);
        tunerPanel.setPreferredSize(new Dimension(getWidth(), (int) tunerPanel.getPreferredSize().getHeight()));
        tunerPanel.setSize(new Dimension(getWidth(), (int) tunerPanel.getPreferredSize().getHeight()));
        setVisible(true);

        executor.execute(() -> {
            player.play(playOnSuccess.getNote());
            if (isRunning.get()) {
                play(-1, 0, 0, null);
            }

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

    private void updateStartStopButton(JButton startStopButton) {
        System.out.println("\nisRunning=" + isRunning.get());
        boolean updated = isRunning.compareAndSet(false, true);
        System.out.println("updatedToTrue=" + updated);
        if (updated) {
            Pitchenation.this.riddle.set(null);
            executor.execute(() -> play(-1, 0, 0, null));
            startStopButton.setText("Stop");
        } else {
            updated = isRunning.compareAndSet(true, false);
            System.out.println("updatedToFalse=" + updated);
            if (updated) {
                riddlePanel.setBackground(null);
                riddleLabel.setText("");
            } else {
                System.out.println("Weird - should not happen");
            }
        }
        if (updated) {
            startStopButton.setText(isRunning.get() ? "Stop" : "Start");
        }
        System.out.println("isRunning=" + isRunning.get());
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
    }

    public static class InputPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        Mixer mixer = null;

        public InputPanel() {
            super(new BorderLayout());
            this.setBorder(new TitledBorder("Input"));
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
                button.setSelected(value == defaultPitchAlgo);
                button.setActionCommand(value.name());
                button.addActionListener(algoChangedListener);
            }
        }
    }

    public static class MyColor extends Color {

        public MyColor(float r, float g, float b) {
            super(r / 100, g / 100, b / 100);
        }

    }

}
