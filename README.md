# OpenChirp
An open source implementation of the [Chirp](chirp.io) data-over-audio protocol.

## What's Chirp?
Back in 2016, the [developers behind Chirp](https://www.chirp.io/about) devised of a protocol for encoding and transmitting data using the audial range, as this would be a common interface between a great many different devices. 

Put simply, a sender and receiver make an _a priori_ agreement of which frequencies correspond to which letters of a shared alphabet. The sender then takes an arbitrary message, maps it to the corresponding frequency range alongside a couple of error handling symbols, whilst the listener transforms the frequencies it hears back into the original data. Provided the channel isn't too lossy, data can be reliably reconstructed back on the listener's side. 

The coolest thing? When you're not sending the symbols in the `40kHz` (ultrasonic, inaudable) range, they sound like a cute little bird singing its goddamn head off.

## What's in these examples?
Each example contains a transmitter and receiver example. Additionally, the example projects are capable of listening to _themselves_, so there's no need for swapping between multiple devices whilst you're just testing stuff out.

## Dependencies
The Chirp protocol uses the [Galois Transform](https://en.wikipedia.org/wiki/Galois_theory) and [Reed-Solomon Encoding](https://en.wikipedia.org/wiki/Reed%E2%80%93Solomon_error_correction) to encode and append redundant symbols to the payload in order to counteract the effects of the lossy surrounding environment using error correction. This is a complicated process, although luckily it's also a widely used one. This repository makes use of [`zxing-core`](https://github.com/zxing/zxing), which applies the `GenericGF` `class` that is used to encode and decode QR codes.

tardosDSP

## Background
There's little lying around about the fundamentals in how Chirp protocol style data is put together; most notably because the official protocol obscures the lower-level information on how signal data is encoded. Take the [chirp-arduino example](https://github.com/chirp/chirp-arduino):

```
void loop() {
   chirp.chirp("parrotbilllllahcm4");
   delay(2000);
}
```

In this example, the Arduino microcontroller will repeatedly chirp the signal `"parrotbilllllahcm4"` every two seconds. This can be split into two parts; the _data frame_ (`parrotbill`) and the _error frame_ (`lllahcm4`). The _error frame_ is actually a function of the _data frame_, since its symbol data is encoded using a Galois polynomial.

Usually, Chirp data is sent alongside an additional _header frame_, which can be used to filter out only to interested listeners. In many examples, this is `hj`, which should be considered as _reserved space_ for the original Chirp protocol.

## Worked Example using _Octave_
The [Chirp Developer Guide](developers.chirp.io/docs/chirps-shortcodes) uses the code `n3a67aai1o` as an example of a data payload. When passed through the encoding process, the resulting identfier is expected to be `n3a67aai1o1miprkd8`.

Below, we break down the encoding process using [_Octave_](https://www.gnu.org/software/octave/) and the [_Communications_](https://octave.sourceforge.io/communications/) extension:

```
# Load the communications package.
pkg load communications 

# Declare the payload within the frame. (All other symbols are 0)
# Note, we use a length(msg1) == 31 array.
# The first 10 characters are the message we wish to send, where
# each number represents the position of our transmission string
# within the alphabet. "0123456789abcdefghijklmnopqrstuv"
msg1 = [23 3 10 6 7 10 10 18 1 24 0 0 0 0 0 0 0 0 0 0 0 0 0]; 

# Declare number of bits used to repsent data. 
# This is what imposes the alpabet size limit (0 ... v).       
m = 5; 

# Define the packet size (2^m - 1) == 31.
n = 2^m-1; 

# Declares the offset of the error bits. 
# Here we pad with zeroes to fill the space in the frame between the payload and error bits,
# since the frame length must be 31 in order for the Reed Solomon decoding process to work.
k = 23; 

# Generate the galois field.
msg=gf([msg1], m); 

# Generate the galois polynomial. (This is a primitive polynomial of D^5+D^2+1 (decimal 37) or 0b00100101).
gen=rsgenpoly(n, k); 

# Encode using Reed-Solomon encoding.
code=rsenc(msg, n, k, gen); 
```
__Note:__ This is example was sourced from the excellent book [_Real-Time Digital Signal Processing: Implementations and Applications_](https://books.google.co.uk/books?id=QIj9Pthp_T8C&pg=PA569&lpg=PA569&dq=reed+solomon+2%5E5&source=bl&ots=kBzfAyfry_&sig=T7AcjbdMjSNXNl1o1ETlAMfDuyg&hl=en&sa=X&ved=0ahUKEwiJu6am4M3WAhXBbRQKHQoPDIg4ChDoAQgnMAA#v=onepage&q=reed%20solomon%202%5E5&f=false).

The resulting value stored into the `code` array is `[23 3 10 6 7 10 10 18 1 24 0 0 0 0 0 0 0 0 0 0 0 0 0 1 22 18 25 27 20 13 8]`. Compensating for zero padding, this can be interpreted as `[23 3 10 6 7 10 10 18 1 24 1 22 18 25 27 20 13 8]`. By cross-referencing these values against the alphabet, you'll see that this array is equivalent to `n3a67aai1o1miprkd8`.


alphabet x, gives polynomial simplest as 



## Interested in contributing?
This is an active project. Currently, we've only provided an example implementation in [Android](https://github.com/Cawfree/OpenChirp/tree/master/android), but due to the diverse nature of sound, there are very many platforms that could be configured as a transmitter or receiver for the protocol. Please drop me a line at `cawfree@gmail.com` if you're interested.

###

// works with chirp share
"hj050422014jikhif"

// Frequency: http://ricardo.cc/2012/12/30/Implementing-the-chirp-protocol-using-webaudio.html
    // Code: https://books.google.co.uk/books?id=QIj9Pthp_T8C&pg=PA569&lpg=PA569&dq=reed+solomon+2%5E5&source=bl&ots=kBzfAyfry_&sig=T7AcjbdMjSNXNl1o1ETlAMfDuyg&hl=en&sa=X&ved=0ahUKEwiJu6am4M3WAhXBbRQKHQoPDIg4ChDoAQgnMAA#v=onepage&q=reed%20solomon%202%5E5&f=false
    // Padding: https://www.cs.cmu.edu/~guyb/realworld/reedsolomon/reed_solomon_codes.html
// Worked Example: https://downloads.bbc.co.uk/rd/pubs/whp/whp-pdf-files/WHP031.pdf


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

/ Full packet is:
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

