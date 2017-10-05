package io.github.cawfree.chirp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;

import java.util.HashMap;
import java.util.Map;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends AppCompatActivity implements PitchDetectionHandler {

    /* Logging. */
    private static final String TAG = "chirp.io";

    /* Protocol Declarations. */
    private static final double                 SEMITONE                     = 1.05946311;
    private static final double                 FREQUENCY_BASE               = 1760;
    private static final String                 ALPHABET                     = "0123456789abcdefghijklmnopqrstuv";
    private static final Map<Character, Double> MAP_CHAR_FREQUENCY           = new HashMap<>();
    private static final Map<Double, Character> MAP_FREQUENCY_CHAR           = new HashMap<>();
    private static final double[]               FREQUENCIES                  = new double[MainActivity.ALPHABET.length()];
    private static final String                 IDENTIFIER                   = "hj";
    private static final int                    LENGTH_IDENTIFIER            = MainActivity.IDENTIFIER.length();
    private static final int                    LENGTH_PAYLOAD               = 10;
    private static final int                    NUM_CORRECTION_BITS          = 8;
    private static final int                    LENGTH_ENCODED               = MainActivity.LENGTH_IDENTIFIER + MainActivity.LENGTH_PAYLOAD + MainActivity.NUM_CORRECTION_BITS;
    private static final int                    LENGTH_FRAME                 = 31;
    private static final int                    PERIOD_MS                    = 120;
    private static final int                    SIZE_READ_BUFFER_PER_ELEMENT = 2;

    /* Sampling Declarations. */
    private static final int WRITE_AUDIO_RATE_SAMPLE_HZ = 44100;
    private static final int WRITE_NUMBER_OF_SAMPLES    = (int)(MainActivity.LENGTH_ENCODED * (MainActivity.PERIOD_MS / 1000.0f) * MainActivity.WRITE_AUDIO_RATE_SAMPLE_HZ) * SIZE_READ_BUFFER_PER_ELEMENT;

    // we aim to make three samples per period


    // Prepare the Frequencies.
    static {
        // Generate the frequencies that correspond to each code point.
        for(int i = 0; i < MainActivity.ALPHABET.length(); i++) {
            // Fetch the Character.
            final char   c              = MainActivity.ALPHABET.charAt(i);
            // Calculate the Frequency.
            final double lFrequency     = MainActivity.FREQUENCY_BASE * Math.pow(MainActivity.SEMITONE, i); /** TODO: to fixed? */
            // Buffer the Frequency.
            MainActivity.FREQUENCIES[i] = lFrequency;
            // Buffer the Frequency.
            MainActivity.MAP_CHAR_FREQUENCY.put(Character.valueOf(c), Double.valueOf(lFrequency));
            MainActivity.MAP_FREQUENCY_CHAR.put(Double.valueOf(lFrequency), Character.valueOf(c));
//            Log.d(TAG, "c "+c+", f "+lFrequency);
        }
    }

    /** Creates a Chirp from a ChirpBuffer. */
    public static final String getChirp(final int[] pChirpBuffer, final int pChirpLength) {
        // Declare the Chirp.
        String lChirp = ""; /** TODO: To StringBuilder. */
        // Buffer the initial characters.
        for(int i = 0; i < pChirpLength; i++) {
            // Update the Chirp.
            lChirp += MainActivity.ALPHABET.charAt(pChirpBuffer[i]);
        }
        // Iterate the ChirpBuffer. (Skip over the zero-padded region.)
        for(int i = (pChirpBuffer.length) - MainActivity.NUM_CORRECTION_BITS; i < pChirpBuffer.length; i++) {
            // Update the Chirp.
            lChirp += MainActivity.ALPHABET.charAt(pChirpBuffer[i]);
        }
        // Return the Chirp.
        return lChirp;
    }

    // hj058042201576ikir

    /* Member Variables. */
    private AudioTrack         mAudioTrack;
    private ReedSolomonEncoder mReedSolomonEncoder;
    private ReedSolomonDecoder mReedSolomonDecoder;
    private AudioDispatcher    mAudioDispatcher;
    private Thread             mAudioThread;
    private boolean            mChirping;
    private double[]           mPitchBuffer;

    @Override
    public final void onCreate(final Bundle pSavedInstanceState) {
        // Implement the Parent.
        super.onCreate(pSavedInstanceState);
        // Define the ContentView.
        this.setContentView(R.layout.activity_main);
        // Allocate the AudioTrack; this is how we'll be generating continuous audio.
        this.mAudioTrack  = new AudioTrack(AudioManager.STREAM_MUSIC, MainActivity.WRITE_AUDIO_RATE_SAMPLE_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, MainActivity.WRITE_NUMBER_OF_SAMPLES, AudioTrack.MODE_STREAM);
        this.mAudioThread = null;
        // Declare the Galois Field. (5-bit, using root polynomial a^5 + a^2 + 1.)
        final GenericGF          lGenericGF          = new GenericGF(0b00100101, MainActivity.LENGTH_FRAME + 1, 1);
        // Allocate the ReedSolomonEncoder and ReedSolomonDecoder.
        this.mReedSolomonEncoder = new ReedSolomonEncoder(lGenericGF);
        this.mReedSolomonDecoder = new ReedSolomonDecoder(lGenericGF);
        // By default, we won't be chirping.
        this.mChirping           = false;
        // Allocate the SampleBuffer, compensating for the size of the read buffer for each data segment.
        this.mPitchBuffer        = new double[MainActivity.LENGTH_ENCODED * MainActivity.SIZE_READ_BUFFER_PER_ELEMENT];
        // Calculate the FrameSize. (In Samples.)
        final int lFrameSize = ((int)((MainActivity.PERIOD_MS / 1000.0f) * MainActivity.WRITE_AUDIO_RATE_SAMPLE_HZ)) / MainActivity.SIZE_READ_BUFFER_PER_ELEMENT;
        // Allocate the AudioDispatcher. (Note; requires dangerous permissions!)
        this.mAudioDispatcher    = AudioDispatcherFactory.fromDefaultMicrophone(MainActivity.WRITE_AUDIO_RATE_SAMPLE_HZ, lFrameSize, 0); /** TODO: Abstract constants. */
        // Register a PitchProcessor with the AudioDispatcher.
        this.getAudioDispatcher().addAudioProcessor(new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, MainActivity.WRITE_AUDIO_RATE_SAMPLE_HZ, (lFrameSize), this));
        // Register an OnTouchListener.
        this.findViewById(R.id.rl_activity_main).setOnClickListener(new View.OnClickListener() { @Override public final void onClick(final View pView) {
            // Are we not already chirping?
            if(!MainActivity.this.isChirping()) {
                // Declare the Message.
                final String lMessage = "hjparrotbill";//"parrotbill"; // hj05142014
                // hj050422014jikh with error correction 2 bit
                // Chirp the message.
                MainActivity.this.chirp(lMessage); //  a full message is 20 characters: 2 for det, 10 for payload, 8 for error detection
//            try {
//                // Decode the data, compensating for error correction.
//                getReedSolomonDecoder().decode(dat, MainActivity.NUM_CORRECTION_BITS);
//            } catch (ReedSolomonException e) {
//                e.printStackTrace();
//            }
            }
        } });
    }

    /** Prints the equivalent information representation of a data string. */
    @SuppressWarnings("unused") public static final void indices(final String pData, final int[] pBuffer, final int pOffset) {
        // Iterate the Data.
        for(int i = 0; i < pData.length(); i++) {
            // Update the contents of the Array.
            pBuffer[pOffset + i] = MainActivity.ALPHABET.indexOf(Character.valueOf(pData.charAt(i)));
        }
    }

    /** Handles an update in Pitch measurement. */
    @Override public final void handlePitch(final PitchDetectionResult pPitchDetectionResult, final AudioEvent pAudioEvent) {
        // Fetch the Pitch.
        final float lPitch = pPitchDetectionResult.getPitch();
        // Valid Pitch?
        if(lPitch != -1 && lPitch >= MainActivity.FREQUENCY_BASE) {
            // Update the SampleBuffer.
            MainActivity.this.onUpdatePitchBuffer(lPitch);
//            // Print it.
//            Log.d(TAG, pPitchDetectionResult.getPitch() + " -> " + lCharacter);
        }
    }

    /** Updates the Sample Buffer. */
    private final void onUpdatePitchBuffer(final double pPitch) {
        // Shift all of the frequencies along.
        for(int i = 0; i < this.getPitchBuffer().length - 1; i++) {
            // Slide the values along.
            this.getPitchBuffer()[i] = this.getPitchBuffer()[i + 1];
        }
        // Append the Sample to the end of the Sample Buffer.
        this.getPitchBuffer()[this.getPitchBuffer().length - 1] = pPitch;
        // Determine if the header has been detected.
        /** TODO: Array-based. */
        if(this.getCharacterAt(0).equals(MainActivity.IDENTIFIER.charAt(0)) && this.getCharacterAt(1).equals(MainActivity.IDENTIFIER.charAt(1))) {
            // Prepare the String.
            String lMessage = "";
            // Iterate the Data.
            for(int i = 0; i < MainActivity.LENGTH_ENCODED; i++) { /** TODO: Fetch indices directly instead. */
                // Append the read characters.
                lMessage += this.getCharacterAt(i);
            }
            // Declare the ResultBuffer.
            final int[] lResult = new int[MainActivity.LENGTH_FRAME];
            // Iterate the data-style characters.
            for(int i = 0; i < MainActivity.LENGTH_IDENTIFIER + MainActivity.LENGTH_PAYLOAD; i++) {
                // Update the Result with the corresponding index value.
                lResult[i] = MainActivity.ALPHABET.indexOf(lMessage.charAt(i));
            }
            // Iterate the error correction characters.
            for(int i = 0; i < MainActivity.NUM_CORRECTION_BITS; i++) {
                // Update the Result with the corresponding index value.
                lResult[(lResult.length - NUM_CORRECTION_BITS) + i] = MainActivity.ALPHABET.indexOf(lMessage.charAt( MainActivity.LENGTH_IDENTIFIER + MainActivity.LENGTH_PAYLOAD + i));
            }

            try {
                // Attempt to decode the result.
                this.getReedSolomonDecoder().decode(lResult, MainActivity.NUM_CORRECTION_BITS);
                // Reset the Message.
                lMessage = "";
                // Iterate the Result.
                for(int i = 0; i < MainActivity.LENGTH_IDENTIFIER + MainActivity.LENGTH_PAYLOAD; i++) {
                    // Buffer the decoded character.
                    lMessage += MainActivity.ALPHABET.charAt(lResult[i]);
                }
                // Log the detected chirp.
                Log.d(TAG, "Rx(" + lMessage + ")");
            }
            catch (final ReedSolomonException pReedSolomonException) {
                // Here, we ignore undetected chirps.
                // Print the Stack Trace.
                //pReedSolomonException.printStackTrace();
            }


            // Next, decode the String.



//            // Print the Message.
//            Log.d(TAG, lMessage);
        }
    }

    /** Returns the character at a normalized index. (Compensates for the fact that the PitchBuffer is a multiple of samples per index.) */
    private final Character getCharacterAt(final int pIndex) {
        // Fetch the Frequencies.
        final double    lFo = this.getPitchBuffer()[(pIndex * MainActivity.SIZE_READ_BUFFER_PER_ELEMENT) + 0];
        final double    lFa = this.getPitchBuffer()[(pIndex * MainActivity.SIZE_READ_BUFFER_PER_ELEMENT) + 1];
        // Fetch the Characters.
        final Character lCo = this.getCharacterFor(lFo);
        final Character lCa = this.getCharacterFor(lFa);
        // Are they equal?
        if(lCo == lCa) {
            // Return the Character.
            return lCo;
        }
        else {
            // Return the character for the average frequency.
            return this.getCharacterFor((lFo + lFa) / 2.0);
        }
    }

    /** Returns the Character corresponding to a Frequency. */
    private final Character getCharacterFor(final double pPitch) {
        // Declare search metrics.
        double lDistance = Double.POSITIVE_INFINITY;
        int    lIndex    = -1;
        // Iterate the Frequencies.
        for(int i = 0; i < MainActivity.FREQUENCIES.length; i++) {
            // Fetch the Frequency.
            final Double lFrequency = MainActivity.FREQUENCIES[i];
            // Calculate the Delta.
            final double lDelta     = Math.abs(pPitch - lFrequency);
            // Is the Delta smaller than the current distance?
            if(lDelta < lDistance) {
                // Overwrite the Distance.
                lDistance = lDelta;
                // Track the Index.
                lIndex    = i;
            }
        }
        // Fetch the corresponding character.
        final Character lCharacter = MainActivity.MAP_FREQUENCY_CHAR.get(Double.valueOf(MainActivity.FREQUENCIES[lIndex]));
        // Return the Character.
        return lCharacter;
    }

    /** Handle resumption of the Activity. */
    @Override protected final void onResume() {
        // Implement the Parent.
        super.onResume();
        // Allocate the AudioThread.
        this.setAudioThread(new Thread(this.getAudioDispatcher()));
        // Start the AudioThread.
        this.getAudioThread().start();
    }

    @Override
    protected final void onPause() {
        // Implement the Parent.
        super.onPause();
        // Stop the AudioDispatcher; implicitly stops the owning Thread.
//        this.getAudioDispatcher().stop();
    }

    /** Encodes and generates a chirp Message. */
    public final void chirp(final String pMessage) {
        // Assert that we're transmitting the Message. (Don't show error checksum codewords.)
        Log.d(TAG, "Tx(" + pMessage + ")");
        // Declare the ChirpBuffer.
        final int[] lChirpBuffer = new int[MainActivity.LENGTH_FRAME];
        // Fetch the indices of the Message.
        MainActivity.indices(pMessage, lChirpBuffer, 0);
        // Encode the Bytes.
        MainActivity.this.getReedSolomonEncoder().encode(lChirpBuffer, MainActivity.NUM_CORRECTION_BITS);
        // Return the Chirp.
        final String lChirp = MainActivity.getChirp(lChirpBuffer, pMessage.length()); // "hj050422014jikhif"; (This will work with Chirp Share!)
        // Chirp-y. (Period is in milliseconds.)
        MainActivity.this.chirp(lChirp, MainActivity.PERIOD_MS);
    }

    /** Produces a chirp. */
    private final void chirp(final String pEncodedChirp, final int pPeriod) {
        // Declare an AsyncTask which we'll use for generating audio.
        final AsyncTask lAsyncTask = new AsyncTask<Void, Void, Void>() {
            /** Initialize the play. */
            @Override protected final void onPreExecute() {
                // Assert that we're chirping.
                MainActivity.this.setChirping(true);
                // Play the AudioTrack.
                MainActivity.this.getAudioTrack().play();
            }
            /** Threaded audio generation. */
            @Override protected Void doInBackground(final Void[] pIsUnused) {
                // Re-buffer the new tone.
                final byte[] lChirp = MainActivity.this.onGenerateChirp(pEncodedChirp, pPeriod);
                // Write the Chirp to the Audio buffer.
                MainActivity.this.getAudioTrack().write(lChirp, 0, lChirp.length);
                // Satisfy the parent.
                return null;
            }
            /** Cyclic. */
            @Override protected final void onPostExecute(Void pIsUnused) {
                // Stop the AudioTrack.
                MainActivity.this.getAudioTrack().stop();
                // Assert that we're no longer chirping.
                MainActivity.this.setChirping(false);
            }
        };
        // Execute the AsyncTask on the pre-prepared ThreadPool.
        lAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
    }

    /** Generates a tone for the audio stream. */
    private final byte[] onGenerateChirp(final String pData, final int pPeriod) {
        // Define the ErrorCorrection.
        final String   lErrorCorrection     = "";
        // Declare the Transmission String.
        final String   lTransmission        = pData + lErrorCorrection;
        // Calculate the Number of Samples per chirp.
        final int      lNumberOfSamples = (int)(MainActivity.WRITE_AUDIO_RATE_SAMPLE_HZ * (pPeriod / 1000.0f));
        // Declare the SampleArray.
              double[] lSampleArray     = new double[lTransmission.length() * lNumberOfSamples];
        // Declare the Generation.
        final byte[]   lGeneration      = new byte[lSampleArray.length * 2];
        // Declare the Offset.
        int      lOffset          = 0;
        // Iterate the Transmission.
        for(int i = 0; i < lTransmission.length(); i++) {
            // Fetch the Data.
            final Character lData      = Character.valueOf(lTransmission.charAt(i));
            // Fetch the Frequency.
            final double    lFrequency = MainActivity.MAP_CHAR_FREQUENCY.get(lData);
            // Iterate the NumberOfSamples. (Per chirp data.)
            for(int j = 0; j < lNumberOfSamples; j++) {
                // Update the SampleArray.
                lSampleArray[lOffset] = Math.sin(2 * Math.PI * j / (MainActivity.WRITE_AUDIO_RATE_SAMPLE_HZ / lFrequency));
                // Increase the Offset.
                lOffset++;
            }
        }
        // Reset the Offset.
        lOffset = 0;
        // Declare the RampWidth.
        final int lRw = (int)(lNumberOfSamples * 0.04);
        // Iterate between each sample.
        for(int i = 0; i < lTransmission.length(); i++) {
            // Fetch the Start and End Indexes of the Sample.
            final int lIo =   i * lNumberOfSamples;
            final int lIa = lIo + lNumberOfSamples;
            // Iterate the Ramp.
            for(int j = 0; j < lRw; j++) {
                // Calculate the progression of the Ramp.
                final double lP = j / (double)lRw;
                // Scale the corresponding samples.
                lSampleArray[lIo + j + 0] *= lP;
                lSampleArray[lIa - j - 1] *= lP;
            }
        }

        // Declare the filtering constant.
        final double lAlpha    = 0.6;
              double lPrevious = 0;

        // Iterate the SampleArray.
        for(int i = 0; i < lSampleArray.length; i++) {
            // Fetch the Value.
            final double lValue    = lSampleArray[i];
            // Filter the Value.
            final double lFiltered = (lAlpha < 1.0) ? ((lValue - lPrevious) * lAlpha) : lValue;
            // Assume normalized, so scale to the maximum amplitude.
            final short lPCM = (short) ((lFiltered * 32767));
            // Supply the Generation with 16-bit PCM. (The first byte is the low-order byte.)
            lGeneration[lOffset++] = (byte) (lPCM & 0x00FF);
            lGeneration[lOffset++] = (byte)((lPCM & 0xFF00) >>> 8);
            // Overwrite the Previous with the Filtered value.
            lPrevious = lFiltered;
        }
        // Return the Generation.
        return lGeneration;
    }

    /** Converts a String to a corresponding alphabetized index array implementation. */
    public static final String array(final String pString) {
        // Convert the String to an equivalent Array.
        final int[] lArray = new int[pString.length()];
        // Iterate the Data.
        for(int i = 0; i < pString.length(); i++) {
            // Update the Array.
            lArray[i] = MainActivity.ALPHABET.indexOf(pString.charAt(i));
        }
        // Return the Array.
        return MainActivity.array(lArray);
    }

    public static final String array(final int[] pArray) {
        // Define the Index equivalent.
        String lIndices = "[";
        // Iterate the Data.
        for(final int i : pArray) {
            // Update the Indices.
            lIndices += " " + i;
        }
        // Close the Array.
        lIndices += " ]";
        // Return the Array.
        return lIndices;
    }

    /* Getters. */
    private final AudioTrack getAudioTrack() {
        return this.mAudioTrack;
    }

    private final ReedSolomonDecoder getReedSolomonDecoder() {
        return this.mReedSolomonDecoder;
    }

    private final ReedSolomonEncoder getReedSolomonEncoder() {
        return this.mReedSolomonEncoder;
    }

    private AudioDispatcher getAudioDispatcher() {
        return this.mAudioDispatcher;
    }

    private final void setAudioThread(final Thread pThread) {
        this.mAudioThread = pThread;
    }

    private final Thread getAudioThread() {
        return this.mAudioThread;
    }

    private final void setChirping(final boolean pIsChirping) {
        this.mChirping = pIsChirping;
    }

    private final boolean isChirping() {
        return this.mChirping;
    }

    private final double[] getPitchBuffer() {
        return this.mPitchBuffer;
    }

}