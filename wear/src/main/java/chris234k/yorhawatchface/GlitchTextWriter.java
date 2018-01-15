package chris234k.yorhawatchface;

import android.os.Handler;
import android.util.Log;

import java.util.Random;

/**
 * Created by Chris on 6/27/2017.
 */

interface ICompletionCallback {
    void onComplete();
}


public class GlitchTextWriter {
    private boolean mIsAnimating;
    private static final int FRAMES_PER_INDEX = 3; // NieR uses 2, but only deals with letters. 3 looks better for numbers.
    private int mTextIndex, mFrameIndex;
    private String mFullText; // The end result, the string we're building towards
    private StringBuilder mCurrentText; // Current string value, as it animates toward mFulString
    private Handler mHandler;
    private final Runnable mRunnable;
    private final long mTextDrawRate;

    private ICompletionCallback mCompletionCallback;

    // NieR selects random characters from the end result string (mFullText)
    // Instead, we select random characters from all valid time string values
    // With numbers, the effect is much less pronounced:
    // 10:00:00 only has 3 unique chars
    private static final String RANDOM_NUMERIC = "1234567890:";

    public GlitchTextWriter(long textDrawRate) {
        mTextIndex = 1;
        mFrameIndex = 1;
        mCurrentText = new StringBuilder();
        mTextDrawRate = textDrawRate;

        mHandler = new Handler();
        mRunnable = new Runnable() {
            @Override
            public void run() {

                // Here's an example progression from NieR
                // L
                // a
                // Lg
                // LL
                // LaL
                // Laa
                // Lang
                // LanL
                // Langg
                // Langg
                // LanguL
                // Langue
                // LanguaL
                // Languag
                // Languaga
                // Language
                //
                // Consecutively display each character in the string
                //
                // For index x
                // Draw correct letters from 0 to x-1
                // Frame 1- display random letter at x
                // Frame 2- display value at index 0 at x
                // Advance index
                //
                // This gives the appearance that index 0 is moving through the string to populate it

                mIsAnimating = true;

                char insertChar;

                if (mFrameIndex < FRAMES_PER_INDEX) {
                    // Roll random character to display for current index
                    Random r = new Random();
                    int randomNum = r.nextInt(RANDOM_NUMERIC.length());
                    insertChar = RANDOM_NUMERIC.charAt(randomNum);
                } else {
                    insertChar = mFullText.charAt(0);
                }

                // Pull substring (assumes mTextIndex > 0)
                String subStr = mFullText.substring(0, mTextIndex);
                mCurrentText.replace(0, mCurrentText.length(), subStr);
                // Replace last char in sub
                mCurrentText.setCharAt(mTextIndex - 1, insertChar);

//                Log.d("yorhawatchface", mTextIndex + " " + mCurrentText);

                if (mFrameIndex == FRAMES_PER_INDEX) {
                    mTextIndex++;
                    mFrameIndex = 1;
                } else {
                    mFrameIndex++;
                }

                if(mTextIndex <= mFullText.length()) {
                    mHandler.postDelayed(mRunnable, mTextDrawRate);
                } else {
                    mIsAnimating = false;
                    mCompletionCallback.onComplete();
                }
            }
        };
    }

    public boolean getIsAnimating() {
        return mIsAnimating;
    }

    public String getTextValue() {
        return mCurrentText.toString();
    }

    public void animateText(String text, long delayMillis, ICompletionCallback completionCallback) {
        // Don't allow animations to be interrupted, stopAnimation should be called directly.
        if(!mIsAnimating) {
            mFullText = text;
            mCurrentText.replace(0, mCurrentText.length(), mFullText);
            mTextIndex = 1;
            mFrameIndex = 1;
            mIsAnimating = true;
            mCompletionCallback = completionCallback;

            mHandler.removeCallbacks(mRunnable);
            mHandler.postDelayed(mRunnable, delayMillis);
        }
    }

    public void stopAnimation() {
        if(mIsAnimating) {
            mHandler.removeCallbacks(mRunnable);
            mIsAnimating = false;
        }
    }
}
