package io.github.cawfree.chirp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Frequency: http://ricardo.cc/2012/12/30/Implementing-the-chirp-protocol-using-webaudio.html
    // Code: https://books.google.co.uk/books?id=QIj9Pthp_T8C&pg=PA569&lpg=PA569&dq=reed+solomon+2%5E5&source=bl&ots=kBzfAyfry_&sig=T7AcjbdMjSNXNl1o1ETlAMfDuyg&hl=en&sa=X&ved=0ahUKEwiJu6am4M3WAhXBbRQKHQoPDIg4ChDoAQgnMAA#v=onepage&q=reed%20solomon%202%5E5&f=false
    // Padding: https://www.cs.cmu.edu/~guyb/realworld/reedsolomon/reed_solomon_codes.html
    // Worked Example: https://downloads.bbc.co.uk/rd/pubs/whp/whp-pdf-files/WHP031.pdf

    int x = 0b00100101; // GF(2^5) array. Primitive Polynomial = D^5+D^2+1. (d37).

    /** Octave Generation (sudo apt-get update, sudo apt-get install octave octave-communications):
     *
     >> pkg load communications
     >> msg1 = [23 3 10 6 7 10 10 18 1 24 0 0 0 0 0 0 0 0 0 0 0 0 0];
     >> m = 5;
     >> n = 2^m-1;
     >> k = 23;
     >> msg=gf([msg1], m);
     >> gen=rsgenpoly(n, k);
     >> code=rsenc(msg, n, k, gen);
     *
     * **/

    // https://projecteuclid.org/download/pdf_1/euclid.bams/1183418619 List of Galois Tables.

    /* Static Declarations. */
    private static final double                 SEMITONE            = 1.05946311;
    private static final double                 FREQUENCY_BASE      = 1760;
    private static final String                 ALPHABET            = "0123456789abcdefghijklmnopqrstuv";
    private static final Map<Character, Double> MAP_FREQUENCY       = new HashMap<>();
    private static final double[]               FREQUENCIES         = new double[MainActivity.ALPHABET.length()];
    private static final int                    LENGTH_FRAME        = 31;
    private static final int                    NUM_CORRECTION_BITS = MainActivity.LENGTH_FRAME - 23;
    private static final int                    LENGTH_DATA         = 10;

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
            MainActivity.MAP_FREQUENCY.put(Character.valueOf(c), Double.valueOf(lFrequency));
//            Log.d("chirp.io", "c: "+c+", f:"+lFrequency);
        }
    }

    /** Creates a Chirp from a ChirpBuffer. */
    public static final String getChirp(final int[] pChirpBuffer) {
        // Declare the Chirp.
        String lChirp = ""; /** TODO: To StringBuilder. */
        // Buffer the initial characters.
        for(int i = 0; i < MainActivity.LENGTH_DATA; i++) {
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

    /* Static Declarations. */
    private static final int AUDIO_RATE_SAMPLE_HZ = 44100;
    private static final int DURATION_SECONDS     = 20;
    private static final int NUMBER_OF_SAMPLES    = MainActivity.DURATION_SECONDS * MainActivity.AUDIO_RATE_SAMPLE_HZ;

    /* Member Variables. */
    private AudioTrack mAudioTrack;
    private int[]      mChirpBuffer;

    @Override
    public final void onCreate(final Bundle pSavedInstanceState) {
        // Implement the Parent.
        super.onCreate(pSavedInstanceState);
        // Define the ContentView.
        this.setContentView(R.layout.activity_main);
        // Allocate the AudioTrack; this is how we'll be generating continuous audio.
        this.mAudioTrack  = new AudioTrack(AudioManager.STREAM_MUSIC, MainActivity.AUDIO_RATE_SAMPLE_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, MainActivity.NUMBER_OF_SAMPLES, AudioTrack.MODE_STREAM);
        this.mChirpBuffer = new int[MainActivity.LENGTH_FRAME];
    }

    /** Prints the equivalent information representation of a data string. */
    @SuppressWarnings("unused") public static final void indices(final String pData, final int[] pBuffer, final int pOffset) {
        // Iterate the Data.
        for(int i = 0; i < MainActivity.LENGTH_DATA; i++) {
            // Update the contents of the Array.
            pBuffer[pOffset + i] = MainActivity.ALPHABET.indexOf(Character.valueOf(pData.charAt(i)));
        }
    }

    /** Handle resumption of the Activity. */
    @Override protected final void onResume() {
        // Implement the Parent.
        super.onResume();

//        final String lMessage = "n3a67aai1o"; /** TODO: Assure exact quantity. */
//        final String lResult  = "n3a67aai1o1miprkd8";

        // Declare the Message.
        final String lMessage = "parrotbill";
        // Print the Message as an array.
        Log.d("chirp.io", "Message: " + MainActivity.array(lMessage));


        // Declare the Galois Field. (5-bit, using root polynomial a^5 + a^2 + 1.)
        final GenericGF          lGenericGF          = new GenericGF(0b00100101, MainActivity.LENGTH_FRAME + 1, 1);
        // Allocate the ReedSolomonEncoder.
        final ReedSolomonEncoder lReedSolomonEncoder = new ReedSolomonEncoder(lGenericGF);
        final ReedSolomonDecoder lReedSolomonDecoder = new ReedSolomonDecoder(lGenericGF);


        // Clear the ChirpBuffer.
        Arrays.fill(this.getChirpBuffer(), 0);
        // Fetch the indices of the Message.
        MainActivity.indices(lMessage, this.getChirpBuffer(), 0);
        // Encode the Bytes.
        lReedSolomonEncoder.encode(this.getChirpBuffer(), MainActivity.NUM_CORRECTION_BITS);
        // Print the contents of the Buffer.
        Log.d("chirp.io", "Buffer:" + MainActivity.array(this.getChirpBuffer()));
        // Return the Chirp.
        final String lChirp = MainActivity.getChirp(this.getChirpBuffer());
        // Chirp-y. (Period is in milliseconds.)
        this.chirp(lChirp, 100); // "hj050422014jikhif"

        try {
            // Decode the Encoded Data.
            lReedSolomonDecoder.decode(this.getChirpBuffer(), 1);
            // Fetch the Response.
            String lResponse = "";
            // Iterate the ChirpBuffer.
            for(int i = 0; i < MainActivity.LENGTH_DATA; i++) {
                // Fetch the equivalent character.
                lResponse += MainActivity.ALPHABET.charAt(this.getChirpBuffer()[i]);
            }
            // Print the decoded element.
            Log.d("chirp.io", lResponse);
        }
        catch(final ReedSolomonException pReedSolomonException) {
            pReedSolomonException.printStackTrace();
        }

        // Full packet is:
        // [17 19 0 5 0 4 2 2 0 1], [0 0 0 0 0 0 0 0 0 0 0 0 0], [4 19 18 20 17 18 15 31]
        // [17 19 0 5 0 4 2 2 0 1    0 0 0 0 0 0 0 0 0 0 0 0 0    4 19 18 20 17 18 15 31]


        // 17 bits... (must be missing one)

        // 32-bit character alphabet
        // n3a67aai1o                = [23 3 10 6 7 10 10 18 1 24]
        // n3a67aai1o1miprkd8        = [23 3 10 6 7 10 10 18 1 24 1 22 18 25 27 20 13 8]

        // GF(2^5) array. Primitive Polynomial = D^5+D^2+1 (decimal 37)

        // Declare the Packet.
//        final int[]              lPacket             = new int[] { 23, 3, 10, 6, 7, 10, 10, 18, 1, 24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1 };





        // Real-Time Digital Signal Processing: Implementations and Applications, p570.
        // https://books.google.co.uk/books?id=QIj9Pthp_T8C&pg=PA569&lpg=PA569&dq=reed+solomon+2%5E5&source=bl&ots=kBzfAyfry_&sig=T7AcjbdMjSNXNl1o1ETlAMfDuyg&hl=en&sa=X&ved=0ahUKEwiJu6am4M3WAhXBbRQKHQoPDIg4ChDoAQgnMAA#v=onepage&q=reed%20solomon%202%5E5&f=false
        // http://ricardo.cc/2012/12/30/Implementing-the-chirp-protocol-using-webaudio.html
        // msg1 = [23 3 10 6 7 10 10 18 1 24]
        // m = 5 # bits per symbol
        // n = 2^m-1 #word lengths for code
        // k = 10 #number of information symbols (i.e. msg1.length)



        // octave is awesome

        // Declare th

        // 10 bits, goes to 18 bits...


        // RS(7, 3)
//        final int n = 7; // codeword length
//        final int k = 3; // message length

        // therefore RS(18, 10) with 5-bit symbols.

        // 32-bit character alphabet
        // n3a67aai1o
        // n3a67aai1o1miprkd8

        //n = 255, k = 223, s = 8
        //2t = 32, t = 16




        // A popular Reed-Solomon code is RS(255,223) with 8-bit symbols.
        // Each codeword contains 255 code word bytes, of which 223 bytes are data and 32 bytes are parity.

        // Configurable:
        //the payload size, in bits
        //the frequency range, in hertz (including a lower and upper bound)
        //the duration, i

        //standard: our classic 50-bit, 1.8s tone in the audible range, which maps tones onto the musical scale.
        // Standard chirps are 10 characters long, from the 5-bit alphabet 0-9a-v
        //ultrasonic: an inaudible chirp, utilising frequencies that are beyond the limit of human hearing but can be sent and received by consumer audio devices.
        //Ultrasonic chirps are 8 characters long, from the 4-bit hexadecimal alphabet 0-9a-f, comprising 32 bits of data in total.


        /*
        A popular Reed-Solomon code is RS(255,223) with 8-bit symbols. Each codeword contains 255 code word bytes, of which 223 bytes are data and 32 bytes are parity. For this code:
        n = 255, k = 223, s = 8
        2t = 32, t = 16


        RS(18, 10)
        n = 18, k = 10, s = 5
        2t = 8, t = 2

        // For example, the maximum length of a code with 8-bit symbols (s=8) is 255 bytes.
        // therefore 31 bytes maximum

        // therefore... we know we're appending 8 bits.. sending 10 = 18
        // the data appends (31 - (8 + 10)) zeros, which are... 13. 13 zeros.

        // Reed-Solomon codes may be shortened by (conceptually) making a number of data symbols zero at the encoder,
        not transmitting them, and then re-inserting them at the decoder.


        // [23 3 10 6 7 10 10 18 1 24 0 0 0 0 0 0 0 0 0 0 0 0 0]

        */

        // GF(2^5) array. Primitive Polynomial = D^5+D^2+1 (decimal 37)
        // 100101

    }

    /** Produces a chirp. */
    public final void chirp(final String pData, final int pPeriod) {
        // Chirp the Data.
        Log.d("chirp.io", "Chirping... " + pData + ", " + MainActivity.array(pData));
        // Declare an AsyncTask which we'll use for generating audio.
        final AsyncTask lAsyncTask = new AsyncTask<Void, Void, Void>() {
            /** Initialize the play. */
            @Override protected final void onPreExecute() {
                // Play the AudioTrack.
                MainActivity.this.getAudioTrack().play();
            }
            /** Threaded audio generation. */
            @Override protected Void doInBackground(final Void[] pIsUnused) {
                // Re-buffer the new tone.
                final byte[] lChirp = MainActivity.this.onGenerateChirp(pData, pPeriod);
                // Write the Chirp to the Audio buffer.
                MainActivity.this.getAudioTrack().write(lChirp, 0, lChirp.length);
                // Satisfy the parent.
                return null;
            }
            /** Cyclic. */
            @Override protected final void onPostExecute(Void pIsUnused) {
                // Stop the AudioTrack.
                MainActivity.this.getAudioTrack().stop();
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
        final int      lNumberOfSamples = (int)(MainActivity.AUDIO_RATE_SAMPLE_HZ * (pPeriod / 1000.0f));
        // Declare the SampleArray.
        final double[] lSampleArray     = new double[lTransmission.length() * lNumberOfSamples];
        // Declare the Generation.
        final byte[]   lGeneration      = new byte[lSampleArray.length * 2];
        // Declare the Offset.
        int      lOffset          = 0;
        // Iterate the Transmission.
        for(int i = 0; i < lTransmission.length(); i++) {
            // Fetch the Data.
            final Character lData      = Character.valueOf(lTransmission.charAt(i));
            // Fetch the Frequency.
            final double    lFrequency = (double)MainActivity.MAP_FREQUENCY.get(lData);
            // Iterate the NumberOfSamples. (Per chirp data.)
            for(int j = 0; j < lNumberOfSamples; j++) {
                // Update the SampleArray.
                lSampleArray[lOffset++] = Math.sin(2 * Math.PI * j / (MainActivity.AUDIO_RATE_SAMPLE_HZ / lFrequency));
            }
        }
        // Reset the Offset.
        lOffset = 0;
        // Iterate the SampleArray.
        for(final double lValue : lSampleArray) {
            // Assume normalized, so scale to the maximum amplitude.
            final short lPCM = (short) ((lValue * 32767));
            // Supply the Generation with 16-bit PCM. (The first byte is the low-order byte.)
            lGeneration[lOffset++] = (byte) (lPCM & 0x00FF);
            lGeneration[lOffset++] = (byte)((lPCM & 0xFF00) >>> 8);
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

    private final int[] getChirpBuffer() {
        return this.mChirpBuffer;
    }

}