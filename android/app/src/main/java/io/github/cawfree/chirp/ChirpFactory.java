package io.github.cawfree.chirp;

/**
 * Created by cawfree on 05/10/17.
 */

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Represents an encoded ChirpFactory, capable of transmission. */
public class ChirpFactory {

    /* Static Declarations. */
    private static final double            SEMITONE               = 1.05946311;
    private static final int               MINIMUM_PERIOD_MS      = 120; /** TODO: Find performance improvements that allow us to beat Chirp. (<100ms). */

    /* Default Declarations. */
    public static final int                DEFAULT_FREQUENCY_BASE = 1760;
    public static final ChirpFactory.Range DEFAULT_RANGE          = new Range("0123456789abcdefghijklmnopqrstuv", 0b00100101, 31); /** TODO: i.e. 2^5 - 1. */
    public static final String             DEFAULT_IDENTIFIER     = "hj";
    public static final int                DEFAULT_LENGTH_PAYLOAD = 10;
    public static final int                DEFAULT_LENGTH_CRC     = 8;
    public static final int                DEFAULT_PERIOD_MS      = ChirpFactory.MINIMUM_PERIOD_MS;


    /** Defines the set of allowable characters for a given protocol. */
    public static class Range {
        /* Member Variables. */
        private final String mCharacters;
        private final int    mGaloisPolynomial;
        private final int    mFrameLength;
        /** Constructor. */
        public Range(final String pCharacters, final int pGaloisPolynomial, final int pFrameLength) {
            // Initialize Member Variables.
            this.mCharacters       = pCharacters;
            this.mGaloisPolynomial = pGaloisPolynomial;
            this.mFrameLength      = pFrameLength;
        }
        /* Getters. */
        public final String getCharacters()       { return this.mCharacters;       }
        public final int    getGaloisPolynomial() { return this.mGaloisPolynomial; }
        public final int    getFrameLength()      { return this.mFrameLength;      }
    }

    /** Factory Pattern. */
    public static final class Builder {
        /* Default Declarations. */
        private double             mBaseFrequency  = ChirpFactory.DEFAULT_FREQUENCY_BASE; // Declares the initial frequency which the range of Chirps is built upon.
        private ChirpFactory.Range mRange          = ChirpFactory.DEFAULT_RANGE;
        private String             mIdentifier     = ChirpFactory.DEFAULT_IDENTIFIER;
        private int                mPayloadLength  = ChirpFactory.DEFAULT_LENGTH_PAYLOAD;
        private int                mErrorLength    = ChirpFactory.DEFAULT_LENGTH_CRC;
        private int                mSymbolPeriodMs = ChirpFactory.DEFAULT_PERIOD_MS;
        /** Builds the ChirpFactory Object. */
        public final ChirpFactory build() throws IllegalStateException {

            /** TODO: Check lengths etc */

            // Allocate and return the ChirpFactory.
            return new ChirpFactory(this.getBaseFrequency(), this.getIdentifier(), this.getRange(), this.getPayloadLength(), this.getErrorLength(), this.getSymbolPeriodMs());
        }
        /* Setters. */
        public final ChirpFactory.Builder setSymbolPeriodMs(final int pSymbolPeriodMs) { this.mSymbolPeriodMs = pSymbolPeriodMs; return this; }
        /* Getters. */
        private final double             getBaseFrequency()  { return this.mBaseFrequency;  }
        private final ChirpFactory.Range getRange()          { return this.mRange;          }
        private final String             getIdentifier()     { return this.mIdentifier;     }
        public final int                 getPayloadLength()  { return this.mPayloadLength;  }
        public final int                 getErrorLength()    { return this.mErrorLength;    }
        public final int                 getSymbolPeriodMs() { return this.mSymbolPeriodMs; }
    }

    /* Member Variables. */
    private final double                 mBaseFrequency;
    private final String                 mIdentifier;
    private final ChirpFactory.Range     mRange;
    private final int                    mPayloadLength;
    private final int                    mErrorLength;
    private final int                    mSymbolPeriodMs;
    private final double[]               mFrequencies; /** TODO: to "tones" */
    private final Map<Character, Double> mMapCharFreq;
    private final Map<Double, Character> mMapFreqChar;

    /** Private construction; force the Builder pattern. */
    private ChirpFactory(final double pBaseFrequency, final String pIdentifier, final ChirpFactory.Range pRange, final int pPayloadLength, final int pErrorLength, final int pSymbolPeriodMs) {
        // Allocate the Frequencies.
        final double[] lFrequencies = new double[pRange.getCharacters().length()];
        // Declare the Mappings. (It's useful to index via either the Character or the Frequency.)
        final Map<Character, Double> lMapCharFreq = new HashMap<>(); /** TODO: Use only a single mapping? */
        final Map<Double, Character> lMapFreqChar = new HashMap<>();
        // Generate the frequencies that correspond to each valid symbol.
        for(int i = 0; i < pRange.getCharacters().length(); i++) {
            // Fetch the Character.
            final char   c               = pRange.getCharacters().charAt(i);
            // Calculate the Frequency.
            final double lFrequency      = pBaseFrequency * Math.pow(ChirpFactory.SEMITONE, i);
            // Buffer the Frequency.
                         lFrequencies[i] = lFrequency;
            // Buffer the Frequency.
            lMapCharFreq.put(Character.valueOf(c), Double.valueOf(lFrequency));
            lMapFreqChar.put(Double.valueOf(lFrequency), Character.valueOf(c));
        }
        // Initialize Member Variables.
        this.mBaseFrequency  = pBaseFrequency;
        this.mIdentifier     = pIdentifier;
        this.mRange          = pRange;
        this.mPayloadLength  = pPayloadLength;
        this.mErrorLength    = pErrorLength;
        this.mSymbolPeriodMs = pSymbolPeriodMs;
        // Assign the Frequencies.
        this.mFrequencies    = lFrequencies; /** TODO: Move to a fn of the MapFreqChar. */
        // Prepare the Mappings. (Make them unmodifiable after initialization.)
        this.mMapCharFreq    = Collections.unmodifiableMap(lMapCharFreq);
        this.mMapFreqChar    = Collections.unmodifiableMap(lMapFreqChar);
    }

    /** Returns the total length of an encoded message. */
    public final int getEncodedLength() {
        // The total packet is comprised as follows: [ID_SYMBOLS + PAYLOAD_SYMBOLS + ERROR_SYMBOLS].
        return this.getIdentifier().length() + this.getPayloadLength() + this.getErrorLength();
    }

    /* Getters. */
    public final double getBaseFrequency() {
        return this.mBaseFrequency;
    }

    public final String getIdentifier() {
        return this.mIdentifier;
    }

    public final double[] getFrequencies() {
        return this.mFrequencies;
    }

    public final Map<Character, Double> getMapCharFreq() {
        return this.mMapCharFreq;
    }

    public final Map<Double, Character> getMapFreqChar() {
        return this.mMapFreqChar;
    }

    public final ChirpFactory.Range getRange() {
        return this.mRange;
    }

    public final int getPayloadLength() {
        return this.mPayloadLength;
    }

    public final int getErrorLength() {
        return this.mErrorLength;
    }

    public final int getSymbolPeriodMs() {
        return this.mSymbolPeriodMs;
    }

}
