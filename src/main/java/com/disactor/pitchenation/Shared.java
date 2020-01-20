package com.disactor.pitchenation;

import java.util.Vector;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Mixer.Info;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class Shared {

    public static Vector<Mixer.Info> getMixerInfo(
            final boolean supportsPlayback, final boolean supportsRecording) {
        final Vector<Mixer.Info> infos = new Vector<>();
        final Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (final Info mixerInfo : mixers) {
            if (supportsRecording && AudioSystem.getMixer(mixerInfo).getTargetLineInfo().length != 0) {
                infos.add(mixerInfo);
            } else if (supportsPlayback && AudioSystem.getMixer(mixerInfo).getSourceLineInfo().length != 0) {
                infos.add(mixerInfo);
            }
        }
        return infos;
    }

    public static String toLocalString(Object info) {
        if (!isWindows())
            return info.toString();
        String defaultEncoding = Charset.defaultCharset().toString();
        try {
            return new String(info.toString().getBytes("windows-1252"), defaultEncoding);
        } catch (UnsupportedEncodingException ex) {
            return info.toString();
        }
    }

    private static String OS = null;

    public static String getOsName() {
        if (OS == null)
            OS = System.getProperty("os.name");
        return OS;
    }

    public static boolean isWindows() {
        return getOsName().startsWith("Windows");
    }
}