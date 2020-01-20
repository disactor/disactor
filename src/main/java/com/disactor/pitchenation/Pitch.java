package com.disactor.pitchenation;

public enum Pitch {

    Do0("Do0", "C0", 16.35),
    Di0("Di0", "C#0", 17.32),
    Re0("Re0", "D0", 18.35),
    Ri0("Ri0", "D#0", 19.45),
    Mi0("Mi0", "E0", 20.6),
    Fa0("Fa0", "F0", 21.83),
    Fi0("Fi0", "F#0", 23.12),
    So0("So0", "G0", 24.5),
    Se0("Se0", "G#0", 25.96),
    La0("La0", "A0", 27.5),
    Li0("Li0", "A#0", 29.14),
    Si0("Si0", "B0", 30.87),
    Do1("Do1", "C1", 32.7),
    Di1("Di1", "C#1", 34.65),
    Re1("Re1", "D1", 36.71),
    Ri1("Ri1", "D#1", 38.89),
    Mi1("Mi1", "E1", 41.2),
    Fa1("Fa1", "F1", 43.65),
    Fi1("Fi1", "F#1", 46.25),
    So1("So1", "G1", 49),
    Se1("Se1", "G#1", 51.91),
    La1("La1", "A1", 55),
    Li1("Li1", "A#1", 58.27),
    Si1("Si1", "B1", 61.74),
    Do2("Do2", "C2", 65.41),
    Di2("Di2", "C#2", 69.3),
    Re2("Re2", "D2", 73.42),
    Ri2("Ri2", "D#2", 77.78),
    Mi2("Mi2", "E2", 82.41),
    Fa2("Fa2", "F2", 87.31),
    Fi2("Fi2", "F#2", 92.5),
    So2("So2", "G2", 98),
    Se2("Se2", "G#2", 103.83),
    La2("La2", "A2", 110),
    Li2("Li2", "A#2", 116.54),
    Si2("Si2", "B2", 123.47),
    Do3("Do3", "C3", 130.81),
    Di3("Di3", "C#3", 138.59),
    Re3("Re3", "D3", 146.83),
    Ri3("Ri3", "D#3", 155.56),
    Mi3("Mi3", "E3", 164.81),
    Fa3("Fa3", "F3", 174.61),
    Fi3("Fi3", "F#3", 185),
    So3("So3", "G3", 196),
    Se3("Se3", "G#3", 207.65),
    La3("La3", "A3", 220),
    Li3("Li3", "A#3", 233.08),
    Si3("Si3", "B3", 246.94),
    Do4("Do4", "C4", 261.63),
    Di4("Di4", "C#4", 277.18),
    Re4("Re4", "D4", 293.66),
    Ri4("Ri4", "D#4", 311.13),
    Mi4("Mi4", "E4", 329.63),
    Fa4("Fa4", "F4", 349.23),
    Fi4("Fi4", "F#4", 369.99),
    So4("So4", "G4", 392),
    Se4("Se4", "G#4", 415.3),
    La4("La4", "A4", 440),
    Li4("Li4", "A#4", 466.16),
    Si4("Si4", "B4", 493.88),
    Do5("Do5", "C5", 523.25),
    Di5("Di5", "C#5", 554.37),
    Re5("Re5", "D5", 587.33),
    Ri5("Ri5", "D#5", 622.25),
    Mi5("Mi5", "E5", 659.25),
    Fa5("Fa5", "F5", 698.46),
    Fi5("Fi5", "F#5", 739.99),
    So5("So5", "G5", 783.99),
    Se5("Se5", "G#5", 830.61),
    La5("La5", "A5", 880),
    Li5("Li5", "A#5", 932.33),
    Si5("Si5", "B5", 987.77),
    Do6("Do6", "C6", 1046.5),
    Di6("Di6", "C#6", 1108.73),
    Re6("Re6", "D6", 1174.66),
    Ri6("Ri6", "D#6", 1244.51),
    Mi6("Mi6", "E6", 1318.51),
    Fa6("Fa6", "F6", 1396.91),
    Fi6("Fi6", "F#6", 1479.98),
    So6("So6", "G6", 1567.98),
    Se6("Se6", "G#6", 1661.22),
    La6("La6", "A6", 1760),
    Li6("Li6", "A#6", 1864.66),
    Si6("Si6", "B6", 1975.53),
    Do7("Do7", "C7", 2093),
    Di7("Di7", "C#7", 2217.46),
    Re7("Re7", "D7", 2349.32),
    Ri7("Ri7", "D#7", 2489.02),
    Mi7("Mi7", "E7", 2637.02),
    Fa7("Fa7", "F7", 2793.83),
    Fi7("Fi7", "F#7", 2959.96),
    So7("So7", "G7", 3135.96),
    Se7("Se7", "G#7", 3322.44),
    La7("La7", "A7", 3520),
    Li7("Li7", "A#7", 3729.31),
    Si7("Si7", "B7", 3951.07),
    Do8("Do8", "C8", 4186.01),
    Di8("Di8", "C#8", 4434.92),
    Re8("Re8", "D8", 4698.63),
    Ri8("Ri8", "D#8", 4978.03),
    Mi8("Mi8", "E8", 5274.04),
    Fa8("Fa8", "F8", 5587.65),
    Fi8("Fi8", "F#8", 5919.91),
    So8("So8", "G8", 6271.93),
    Se8("Se8", "G#8", 6644.88),
    La8("La8", "A8", 7040),
    Li8("Li8", "A#8", 7458.62),
    Si8("Si8", "B8", 7902.13);

    private final String chroma;
    private final String echroma;
    private final String note;
    private final double pitch;
    private final int octave;

    Pitch(String echroma, String note, double pitch) {
        this.echroma = echroma;
        this.note = note;
        this.pitch = pitch;
        this.chroma = echroma.substring(0, 2);
        this.octave = Integer.parseInt(echroma.substring(2, 3));
    }

    public String getEchroma() {
        return echroma;
    }

    public String getChroma() {
        return chroma;
    }

    public int getOctave() {
        return octave;
    }

    public double getPitch() {
        return pitch;
    }

    public String getNote() {
        return note;
    }


}
